package ogss.common.java.internal;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.BoolType;
import ogss.common.java.internal.fieldTypes.F32;
import ogss.common.java.internal.fieldTypes.F64;
import ogss.common.java.internal.fieldTypes.I16;
import ogss.common.java.internal.fieldTypes.I32;
import ogss.common.java.internal.fieldTypes.I64;
import ogss.common.java.internal.fieldTypes.I8;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.java.internal.fieldTypes.V64;
import ogss.common.java.restrictions.FieldRestriction;
import ogss.common.java.restrictions.NonNull;
import ogss.common.java.restrictions.Range;
import ogss.common.java.restrictions.TypeRestriction;
import ogss.common.streams.FileInputStream;

/**
 * The parser implementation is based on the denotational semantics given in TR14§6.
 *
 * @author Timm Felden
 */
abstract class Parser extends StateInitializer {

    /**
     * File size in bytes below which the sequential parser will be used.
     */
    public static int SEQ_LIMIT = 512000;

    final protected PoolBuilder pb;

    /**
     * This buffer provides the association of file fieldID to field.
     */
    protected final ArrayList<Object> fields = new ArrayList<>();

    /**
     * User defined types. This array is used to resolve type IDs while parsing. The type IDs assigned to created
     * entities may not correspond to udts indices (shifted by 10).
     */
    final ArrayList<FieldType<?>> udts = new ArrayList<>();

    public SkillException readErrors;

    Parser(FileInputStream in, PoolBuilder pb) {
        super(in, pb);
        this.pb = pb;

        // G
        {
            final byte first = in.i8();
            // guard is 22 26?
            if (first == 0x22) {
                if (in.i8() != 0x26)
                    throw new ParseException(in, null, "Illegal guard.");

                guard = "";
            }
            // guard is hash?
            else if (first == 0x23) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte next = in.i8();
                while (0 != next) {
                    buf.write(next);
                }
                guard = new String(buf.toByteArray(), StringPool.utf8);

            } else
                throw new ParseException(in, null, "Illegal guard.");
        }

        // S
        try {
            fields.add(Strings);
            int count = in.v32();

            if (0 != count) {
                in.jump(Strings.S(count, in));
            }
        } catch (Exception e) {
            throw new ParseException(in, e, "corrupted string block");
        }

        // T
        typeBlock();

        fixContainerMD();

        // HD
        processData();

        if (!in.eof()) {
            throw new ParseException(in, null, "Expected end of file, but some bytes remain.");
        }
    }

    /**
     * Turns a field type into a preliminary type information. In case of user types, the declaration of the respective
     * user type may follow after the field declaration.
     */
    final FieldType<?> fieldType() {
        final int typeID = in.v32();
        switch (typeID) {
        case 0:
            return BoolType.get();
        case 1:
            return I8.get();
        case 2:
            return I16.get();
        case 3:
            return I32.get();
        case 4:
            return I64.get();
        case 5:
            return V64.get();
        case 6:
            return F32.get();
        case 7:
            return F64.get();
        case 8:
            return Annotation;
        case 9:
            return Strings;
        default:
            return udts.get(typeID - 10);
        }
    }

    /**
     * @return the matching container name
     */
    static final String name(int kind, FieldType<?> b1, FieldType<?> b2) {
        final String name;
        switch (kind) {
        case 0:
            name = b1 + "[]";
            break;
        case 1:
            name = "list<" + b1 + ">";
            break;
        case 2:
            name = "set<" + b1 + ">";
            break;
        case 3:
            name = "map<" + b1 + "," + b2 + ">";
            break;
        default:
            throw new IllegalStateException();
        }
        return name;
    }

    final HashSet<TypeRestriction> typeRestrictions(int i) {
        final HashSet<TypeRestriction> rval = new HashSet<>();
        // parse count many entries
        while (i-- != 0) {
            final int id = in.v32();
            switch (id) {
            case 0:
                // Unique
            case 1:
                // Singleton
            case 2:
                // Monotone
                break;

            default:
                throw new ParseException(in, null,
                        "Found unknown type restriction %d. Please regenerate your binding, if possible.", id);
            }
        }
        return rval;
    }

    final HashSet<FieldRestriction<?>> fieldRestrictions(FieldType<?> t) {
        HashSet<FieldRestriction<?>> rval = new HashSet<FieldRestriction<?>>();
        for (int count = in.v32(); count != 0; count--) {
            final int id = in.v32();
            switch (id) {

            case 0: {
                if (t instanceof ByRefType<?>)
                    rval.add(NonNull.get());
                else
                    throw new ParseException(in, null, "Nonnull restriction on non-refernce type: %s.", t.toString());
                break;
            }

            case 1: {
                // default
                if (t instanceof ByRefType<?>) {
                    // TODO typeId -> ref
                    in.v32();
                } else {
                    // TODO other values
                    t.r(in);
                }
                break;
            }

            case 3: {
                final FieldRestriction<?> r = Range.make(t.typeID, in);
                if (null == r)
                    throw new ParseException(in, null, "Type %s can not be range restricted!", t.toString());
                rval.add(r);
                break;
            }

            case 5: {
                // TODO coding
                // string.get
                in.v32();
                break;
            }

            case 7: {
                // TODO CLP
                break;
            }

            case 9: {
                for (int c = in.v32(); c != 0; c--) {
                    // type IDs
                    in.v32();
                }
                break;
            }

            default:
                throw new ParseException(in, null,
                        "Found unknown field restriction %d. Please regenerate your binding, if possible.", id);
            }
        }
        return rval;
    }

    /**
     * Parse type definitions and merge them into the known type hierarchy
     */
    final void typeDefinitions() {
        int index = 0;
        int THH = 0;
        // the index of the next known class at index THH
        final int[] nextID = new int[32];
        // the nextName, null if there is no next PD
        String nextName = pb.name(0);

        Pool<?> p = null, last = null;
        Pool<?> result = null;

        // Name of all seen class names to prevent duplicate allocation of the same pool.
        final IdentityHashMap<String, Object> seenNames = new IdentityHashMap<>();
        int TCls = in.v32();

        // file state
        String name = null;
        int count = 0;
        Pool<?> superDef = null;
        int bpo = 0;

        for (boolean moreFile = TCls > 0; (moreFile = TCls > 0) || null != nextName; TCls--) {
            // read next pool from file if required
            if (moreFile) {
                // name
                name = Strings.r(in);
                if (null == name)
                    throw new ParseException(in, null, "corrupted file: nullptr in type name");

                // static size
                count = in.v32();

                // attr
                final HashSet<TypeRestriction> attr;
                {
                    final int rc = in.v32();
                    if (0 == rc)
                        attr = new HashSet<>();
                    else
                        attr = typeRestrictions(rc);
                }

                // super
                {
                    final int superID = in.v32();
                    if (0 == superID) {
                        superDef = null;
                        bpo = 0;
                    } else if (superID > classes.size())
                        throw new ParseException(in, null,
                                "Type %s refers to an ill-formed super type.\n"
                                        + "          found: %d; current number of other types %d",
                                name, superID, classes.size());
                    else {
                        superDef = classes.get(superID - 1);
                        bpo = in.v32();
                    }
                }
            }

            // allocate pool
            boolean keepKnown, keepFile = true;
            // TODO do while -> keepFile = TCls != 0
            do {
                keepKnown = null == nextName;

                if (moreFile) {
                    // check common case, i.e. the next class is the expected one
                    if (!keepKnown) {
                        if (superDef == p) {
                            if (name == nextName) {
                                // the next pool is the expected one
                                keepFile = keepKnown = false;

                            } else if (compare(name, nextName) < 0) {
                                // we have to advance the file pool
                                keepFile = false;
                                keepKnown = true;

                            } else {
                                // we have to advance known pools
                                keepKnown = false;
                            }
                        } else {

                            // depending on the files super THH, we can decide if we have to process the files type or
                            // our
                            // type first;
                            // invariant: p != superDef ⇒ superDef.THH != THH
                            // invariant: ∀p. p.next.THH <= p.THH + 1
                            // invariant: ∀p. p.Super = null <=> p.THH = 0
                            if (null != superDef && superDef.THH < THH) {
                                // we have to advance known pools
                                keepKnown = false;

                            } else {
                                // we have to advance the file pool
                                keepFile = false;
                                keepKnown = true;
                            }
                        }
                    } else {
                        // there are no more known pools
                        keepFile = false;
                    }
                } else if (keepKnown) {
                    // we are done
                    return;
                }

                // create the next pool
                if (keepKnown) {
                    // an unknown pool has to be created
                    if (null == superDef) {
                        last = null;
                        result = new SubPool<>(index++, name, UnknownObject.class, null);
                    } else {
                        result = superDef.makeSub(index++, name);
                    }
                    result.bpo = bpo;
                    udts.add(result);
                    classes.add(result);

                    // set next
                    if (null != last) {
                        last.next = result;
                    }
                    last = result;
                } else {
                    if (null == p) {
                        last = null;
                        p = pb.make(nextID[0]++, index++);
                    } else {
                        p = p.makeSub(nextID[THH]++, index++);
                    }
                    // @note this is sane, because it is 0 if p is not part of the type hierarchy of superDef
                    p.bpo = bpo;
                    SIFA[nsID++] = p;
                    classes.add(p);

                    if (!keepFile) {
                        result = p;
                        udts.add(result);
                    } else
                        Strings.add(p.name);

                    // set next
                    if (null != last) {
                        last.next = p;
                    }
                    last = p;

                    // move to next pool
                    {
                        // try to move down to our first child
                        nextName = p.nameSub(nextID[++THH] = 0);

                        // move up until we find a next pool
                        while (null == nextName && THH != 1) {
                            p = p.superPool;
                            nextName = p.nameSub(nextID[--THH]);
                        }
                        // check at base type level
                        if (null == nextName) {
                            p = null;
                            nextName = pb.name(nextID[THH = 0]);
                        }
                    }
                }
            } while (keepFile);

            result.cachedSize = result.staticDataInstances = count;

            // add a null value for each data field to ensure that the temporary size of data fields matches those
            // from file
            int fields = in.v32();
            while (fields-- != 0)
                result.dataFields.add(null);
        }
    }

    final void TContainer() {
        // next type ID
        int tid = 10 + classes.size();
        // KCC index
        int ki = 0;
        // @note it is always possible to construct the next kcc from SIFA
        int kcc = pb.kcc(ki);
        int kkind = 0;
        FieldType<?> kb1 = null, kb2 = null;
        String kname = null;
        if (-1 != kcc) {
            kkind = (kcc >> 30) & 3;
            kb1 = SIFA[kcc & 0x7FFF];
            kb2 = 3 == kkind ? SIFA[(kcc >> 15) & 0x7FFF] : null;
            kname = name(kkind, kb1, kb2);
        }

        for (int count = in.v32(); count != 0; count--) {
            final int kind = in.i8();
            final FieldType<?> b1 = fieldType();
            final FieldType<?> b2 = (3 == kind) ? fieldType() : null;
            final String name = name(kind, b1, b2);

            HullType<?> r = null;
            int cmp = -1;

            // construct known containers until we hit the state of the file
            while (-1 != kcc && (cmp = compare(name, kname)) >= 0) {
                switch (kkind) {
                case 0:
                    r = new ArrayType<>(tid++, kb1);
                    break;
                case 1:
                    r = new ListType<>(tid++, kb1);
                    break;
                case 2:
                    r = new SetType<>(tid++, kb1);
                    break;

                case 3:
                    r = new MapType<>(tid++, kb1, kb2);
                    break;

                default:
                    throw new Error(); // dead
                }
                SIFA[nsID++] = r;
                r.fieldID = nextFieldID++;
                containers.add(r);

                // move to next kcc
                kcc = pb.kcc(++ki);
                if (-1 != kcc) {
                    kkind = (kcc >> 30) & 3;
                    kb1 = SIFA[kcc & 0x7FFF];
                    kb2 = 3 == kkind ? SIFA[(kcc >> 15) & 0x7FFF] : null;
                    kname = name(kkind, kb1, kb2);
                }

                // break loop for perfect matches after the first iteration
                if (0 == cmp)
                    break;
            }

            // the last constructed kcc was not the type from the file
            if (0 != cmp) {
                switch (kind) {
                case 0:
                    r = new ArrayType<>(tid++, b1);
                    break;
                case 1:
                    r = new ListType<>(tid++, b1);
                    break;
                case 2:
                    r = new SetType<>(tid++, b1);
                    break;

                case 3:
                    r = new MapType<>(tid++, b1, b2);
                    break;

                default:
                    throw new SkillException("Illegal container constructor ID: " + kind);
                }

                r.fieldID = nextFieldID++;
                containers.add(r);
            }
            fields.add(r);
            udts.add(r);
        }
    }

    /**
     * Correct and more efficient string compare.
     */
    public static int compare(String L, String R) {
        final int len1 = L.length();
        final int len2 = R.length();
        if (len1 != len2)
            return len1 - len2;

        char v1[] = L.toCharArray();
        char v2[] = R.toCharArray();

        for (int i = 0; i < len1; i++) {
            char c1 = v1[i];
            char c2 = v2[i];
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return 0;
    }

    /**
     * parse T and F
     */
    abstract void typeBlock();

    final void readFields(Pool<?> p) {
        // we have not yet seen a known field
        int ki = 0;

        // we pass the size by adding null's for each expected field in the stream because AL.clear does not return
        // its backing array, i.e. we will likely not resize it that way
        int idx = p.dataFields.size();

        p.dataFields.clear();
        String kfn = p.KFN(0);
        while (0 != idx--) {
            // read field
            final String name = Strings.r(in);
            FieldType<?> t = fieldType();
            HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
            FieldDeclaration<?, ?> f = null;

            while (null != (kfn = p.KFN(ki))) {
                // is it the next known field?
                if (name == kfn) {
                    if ((f = p.KFC(ki++, SIFA, nextFieldID)) instanceof AutoField)
                        throw new ParseException(in, null, "Found transient field %s.%s in the file.", p.name, name);

                    if (f.type != t)
                        throw new ParseException(in, null, "Field %s should have type %s.%s but has type %s", p.name,
                                f.name, f.type, t);

                    break;
                }

                // else, it might be an unknown field
                if (compare(name, kfn) < 0) {
                    // create unknown field
                    f = new LazyField<>(t, name, nextFieldID, p);
                    break;
                }

                // else, it is a known fields not contained in the file
                Strings.add(kfn);

                f = p.KFC(ki++, SIFA, nextFieldID);
                if (!(f instanceof AutoField)) {
                    nextFieldID++;

                    // increase maxDeps
                    if (f.type instanceof HullType<?>) {
                        ((HullType<?>) f.type).maxDeps++;
                    }
                }
                f = null;
            }

            if (null == f) {
                // no known fields left, so it is obviously unknown
                f = new LazyField<>(t, name, nextFieldID, p);
            }

            nextFieldID++;

            // increase maxDeps
            if (f.type instanceof HullType<?>) {
                ((HullType<?>) f.type).maxDeps++;
            }

            f.addRestriction(rest);

            fields.add(f);
        }

        // create remaining auto fields
        if (kfn != null)
            do {
                // nextID wont be used anyway
                p.KFC(ki, SIFA, 0);
            } while (null != p.KFN(++ki));
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    abstract void processData();
}
