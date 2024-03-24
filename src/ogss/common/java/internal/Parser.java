package ogss.common.java.internal;

import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.*;
import ogss.common.java.restrictions.FieldRestriction;
import ogss.common.java.restrictions.NonNull;
import ogss.common.java.restrictions.Range;
import ogss.common.java.restrictions.TypeRestriction;
import ogss.common.jvm.streams.FileInputStream;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;

/**
 * @author Timm Felden
 */
abstract class Parser extends StateInitializer {

    /**
     * File size in bytes below which the sequential parser will be used.
     */
    public static int SEQ_LIMIT = 512000;

    final protected PoolBuilder pb;

    final FileInputStream in;

    /**
     * This buffer provides the association of file fieldID to field.
     */
    protected final ArrayList<Object> fields = new ArrayList<>();

    /**
     * File defined types. This array is used to resolve type IDs while parsing. The type IDs assigned to created
     * entities may not correspond to fdts indices (shifted by 10).
     */
    final ArrayList<FieldType<?>> fdts = new ArrayList<>();

    public OGSSException readErrors;

    Parser(FileInputStream in, PoolBuilder pb) {
        super(pb);
        this.pb = pb;
        this.in = in;

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
                for (byte next = in.i8(); 0 != next; next = in.i8()) {
                    buf.write(next);
                }
                guard = new String(buf.toByteArray(), StringPool.utf8);

            } else
                throw new ParseException(in, null, "Illegal guard.");
        }

        // S
        try {
            fields.add(Strings);
            Strings.readSL(in);
        } catch (Exception e) {
            throw new ParseException(in, e, "corrupted string block");
        }

        // T
        try {
            typeBlock();
        } catch (Exception e) {
            throw new ParseException(in, e, "corrupted type block");
        }

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
            return AnyRef;
        case 9:
            return Strings;
        default:
            return fdts.get(typeID - 10);
        }
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

        for (boolean moreFile = TCls > 0; (moreFile = TCls > 0) | null != nextName; TCls--) {
            // read next pool from file if required
            if (moreFile) {
                // name
                name = Strings.idMap.get(in.v32());

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
                    } else if (superID > fdts.size())
                        throw new ParseException(in, null,
                                "Type %s refers to an ill-formed super type.\n"
                                        + "          found: %d; current number of other types %d",
                                name, superID, fdts.size());
                    else {
                        superDef = (Pool<?>) fdts.get(superID - 1);
                        bpo = in.v32();
                    }
                }
            }

            // allocate pool
            boolean keepKnown, keepFile = !moreFile;
            do {
                keepKnown = null == nextName;

                if (moreFile) {
                    // check common case, i.e. the next class is the expected one
                    if (!keepKnown) {
                        if (superDef == p) {
                            if (name == nextName) {
                                // the next pool is the expected one
                                keepFile = false;

                            } else if (compare(name, nextName) < 0) {
                                // we have to advance the file pool
                                keepKnown = true;
                                keepFile = false;

                            } else {
                                // we have to advance known pools
                                keepFile = true;
                            }
                        } else {

                            // depending on the files super THH, we can decide if we have to process the files type or
                            // our type first;
                            // invariant: p != superDef ⇒ superDef.THH != THH
                            // invariant: ∀p. p.next.THH <= p.THH + 1
                            // invariant: ∀p. p.Super = null <=> p.THH = 0
                            if (null != superDef && superDef.THH < THH) {
                                // we have to advance known pools
                                keepFile = true;

                            } else {
                                // we have to advance the file pool
                                keepKnown = true;
                                keepFile = false;
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
                    if (null != superDef) {
                        result = superDef.makeSub(index++, name);
                    } else {
                        last = null;
                        result = new SubPool<>(index++, name, UnknownObject.class, null);
                    }
                    result.bpo = bpo;
                    fdts.add(result);
                    classes.add(result);

                    // set next
                    if (null != last) {
                        last.next = result;
                    }
                    last = result;
                } else {
                    if (null != p) {
                        p = p.makeSub(nextID[THH]++, index++);
                    } else {
                        last = null;
                        p = pb.make(nextID[0]++, index++);
                    }
                    // @note this is sane, because it is 0 if p is not part of the type hierarchy of superDef
                    p.bpo = bpo;
                    SIFA[nsID++] = p;
                    classes.add(p);

                    if (!keepFile) {
                        result = p;
                        fdts.add(result);
                    }

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
                        while (null == nextName & THH != 1) {
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
                // check for duplicate adds
                if (seenNames.containsKey(last.name)) {
                    throw new OGSSException("duplicate definition of type " + last.name);
                }
                seenNames.put(last.name, null);
            } while (keepFile);

            result.cachedSize = result.staticDataInstances = count;

            // add a null value for each data field to ensure that the temporary size of data fields matches those
            // from file
            int fields = in.v32();
            while (fields-- != 0)
                result.dataFields.add(null);
        }
    }

    /**
     * turn kcc into ucc; this is always possible for the next type
     *
     * @return the UCC for a given kcc
     */
    final static int toUCC(int kind, FieldType<?> b1, FieldType<?> b2) {
        int baseTID1 = b1.typeID;
        int baseTID2 = null == b2 ? 0 : b2.typeID;
        if (baseTID2 < baseTID1)
            return (baseTID1 << 17) | (kind << 15) | baseTID2;

        return (baseTID2 << 17) | (kind << 15) | baseTID1;
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
        // @note using int here means that UCC may only contain TIDs < 2^14
        int lastUCC = 0;
        int kucc = 0;
        if (-1 != kcc) {
            kkind = (kcc >> 30) & 3;
            kb1 = SIFA[kcc & 0x7FFF];
            kb2 = 3 == kkind ? SIFA[(kcc >> 15) & 0x7FFF] : null;
            kucc = toUCC(kkind, kb1, kb2);
        }

        for (int count = in.v32(); count != 0; count--) {
            final int fkind = in.i8();
            final FieldType<?> fb1 = fieldType();
            final FieldType<?> fb2 = (3 == fkind) ? fieldType() : null;
            final int fucc = toUCC(fkind, fb1, fb2);

            ContainerType<?> r = null;
            int cmp = -1;

            // construct known containers until we hit the state of the file
            while (-1 != kcc && (cmp = (fucc - kucc)) >= 0) {
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

                // check UCC order
                if (lastUCC > kucc) {
                    throw new ParseException(in, null, "File is not UCC-ordered.");
                }
                lastUCC = kucc;

                // move to next kcc
                kcc = pb.kcc(++ki);
                if (-1 != kcc) {
                    kkind = (kcc >> 30) & 3;
                    kb1 = SIFA[kcc & 0x7FFF];
                    kb2 = 3 == kkind ? SIFA[(kcc >> 15) & 0x7FFF] : null;
                    kucc = toUCC(kkind, kb1, kb2);
                }

                // break loop for perfect matches after the first iteration
                if (0 == cmp)
                    break;
            }

            // the last constructed kcc was not the type from the file
            if (0 != cmp) {
                switch (fkind) {
                case 0:
                    r = new ArrayType<>(tid++, fb1);
                    break;
                case 1:
                    r = new ListType<>(tid++, fb1);
                    break;
                case 2:
                    r = new SetType<>(tid++, fb1);
                    break;

                case 3:
                    r = new MapType<>(tid++, fb1, fb2);
                    break;

                default:
                    throw new OGSSException("Illegal container constructor ID: " + fkind);
                }

                r.fieldID = nextFieldID++;
                containers.add(r);

                // check UCC order
                if (lastUCC > fucc) {
                    throw new ParseException(in, null, "File is not UCC-ordered.");
                }
                lastUCC = fucc;
            }
            fields.add(r);
            fdts.add(r);
        }

        // construct remaining known containers
        while (-1 != kcc) {
            final ContainerType<?> r;
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

            // check UCC order
            if (lastUCC > kucc) {
                throw new ParseException(in, null, "File is not UCC-ordered.");
            }
            lastUCC = kucc;

            // move to next kcc
            kcc = pb.kcc(++ki);
            if (-1 != kcc) {
                kkind = (kcc >> 30) & 3;
                kb1 = SIFA[kcc & 0x7FFF];
                kb2 = 3 == kkind ? SIFA[(kcc >> 15) & 0x7FFF] : null;
                kucc = toUCC(kkind, kb1, kb2);
            }
        }
    }

    final void TEnum() {
        // next type ID
        int tid = 10 + classes.size() + containers.size();

        int ki = 0;
        String nextName = pb.enumName(ki);
        EnumPool<?> r;
        // create enums from file
        for (int count = in.v32(); count != 0; count--) {
            String name = Strings.idMap.get(in.v32());
            int vcount = in.v32();
            if (vcount <= 0)
                throw new ParseException(in, null, "Enum %s is too small.", name);

            String[] vs = new String[vcount];
            for (int i = 0; i < vcount; i++) {
                vs[i] = Strings.idMap.get(in.v32());
            }

            int cmp = null != nextName ? compare(name, nextName) : -1;

            while (true) {
                if (0 == cmp) {
                    r = new EnumPool(tid++, name, vs, pb.enumMake(ki++));
                    enums.add(r);
                    fdts.add(r);
                    SIFA[nsID++] = r;
                    nextName = pb.enumName(ki);
                    break;

                } else if (cmp < 1) {
                    r = new EnumPool(tid++, name, vs, null);
                    enums.add(r);
                    fdts.add(r);
                    break;
                }

                r = new EnumPool(tid++, nextName, null, pb.enumMake(ki++));
                enums.add(r);
                SIFA[nsID++] = r;
                nextName = pb.enumName(ki);
                cmp = null != nextName ? compare(name, nextName) : -1;
            }
        }
        // create remaining known enums
        while (null != nextName) {
            r = new EnumPool(tid++, nextName, null, pb.enumMake(ki++));
            enums.add(r);
            SIFA[nsID++] = r;
            nextName = pb.enumName(ki);
        }
    }

    /**
     * Correct and more efficient string compare.
     */
    public static int compare(String L, String R) {
        if (L == R)
            return 0;

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
            final String name = Strings.idMap.get(in.v32());
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

                // else, it is a known field not contained in the file
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

            f.restrictions.addAll((HashSet) rest);

            fields.add(f);
        }

        // create remaining known fields
        while (null != p.KFN(ki)) {
            FieldDeclaration<?, ?> f = p.KFC(ki, SIFA, nextFieldID);
            if (!(f instanceof AutoField)) {
                nextFieldID++;

                // increase maxDeps
                if (f.type instanceof HullType<?>) {
                    ((HullType<?>) f.type).maxDeps++;
                }
            }
            ki++;
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    abstract void processData();
}
