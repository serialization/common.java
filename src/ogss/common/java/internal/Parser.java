package ogss.common.java.internal;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.BoolType;
import ogss.common.java.internal.fieldTypes.F32;
import ogss.common.java.internal.fieldTypes.F64;
import ogss.common.java.internal.fieldTypes.I16;
import ogss.common.java.internal.fieldTypes.I32;
import ogss.common.java.internal.fieldTypes.I64;
import ogss.common.java.internal.fieldTypes.I8;
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

    final private PoolBuilder pb;

    // the index of the next known class
    protected int nextID;
    // the nextName, null if there is no next PD
    protected String nextName;

    /**
     * name of all known classes to distinguish between known and unknown classes from the file spec
     * 
     * @note created on first use
     */
    protected HashSet<String> knownNames;

    /**
     * This buffer provides the association of file fieldID to field.
     */
    protected ArrayList<Object> fields = new ArrayList<>();

    /**
     * User defined types. This array is used to resolve type IDs while parsing. The type IDs assigned to created
     * entities may not correspond to udts indices (shifted by 10).
     */
    final ArrayList<FieldType<?>> udts = new ArrayList<>();

    public SkillException readErrors;

    Parser(FileInputStream in, int sifaSize, PoolBuilder pb, KCC[] kccs) {
        super(in, sifaSize, kccs);
        this.pb = pb;
        nextName = pb.name(0);

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

    final void typeDefinition() {

        // name
        final String name = Strings.r(in);
        if (null == name)
            throw new ParseException(in, null, "corrupted file: nullptr in type name");

        // static size
        final int count = in.v32();

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
        final Pool<?> superDef;
        final int bpo;
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

        // allocate pool
        final Pool<?> result;
        while (true) {

            // check common case, i.e. the next class is the expected one
            if (nextName == name) {

                try {
                    SIFA[nextID] = result = pb.make(nextID, classes, superDef);
                } catch (Exception e) {
                    throw new ParseException(in, e, "Failed to instantiate known class " + name);
                }
                if (superDef != result.superPool) {
                    throw new ParseException(in, null, "Class %s has no super type but the file defines super type %s",
                            name, superDef.name);
                }

                // move on
                nextName = pb.name(++nextID);

            } else {
                // ensure that the name has not been used before
                if (TBN.containsKey(name)) {
                    throw new ParseException(in, null, "Duplicate definition of class " + name);
                }

                // ensure that knownNames is filled with known names
                if (null == knownNames) {
                    knownNames = new HashSet<>();
                    String n;
                    for (int i = 0; null != (n = pb.name(i)); i++)
                        knownNames.add(n);
                }

                final boolean known = knownNames.contains(name);
                if (known) {
                    // the class has a known name, has not been declared before and is not the expected class
                    // therefore, we have to allocate all classes
                    // @note nextPD cannot be null here, because there still is a known class which is not a duplicate
                    while (nextName != name) {
                        Strings.add(nextName);

                        // create p from nextPD and our current state
                        Pool<?> p = pb.make(nextID, classes, null);
                        SIFA[nextID] = p;

                        // note: p will not receive data fields; this is exactly, what we intend here
                        // note: bpo/sizes are not set, because zero-allocation is correct there

                        classes.add(p);
                        TBN.put(p.name, p);
                        // move on
                        nextName = pb.name(++nextID);
                    }
                    // the next class is that obtained from file, so jump back to the start of the loop
                    continue;

                }

                // the pool is not known
                final int idx = classes.size();
                if (null == superDef) {
                    result = new SubPool<>(idx, name, UnknownObject.class, null);
                } else {
                    result = superDef.makeSubPool(idx, name);
                }
            }

            udts.add(result);
            classes.add(result);
            TBN.put(name, result);
            break;
        }

        result.bpo = bpo;
        result.cachedSize = result.staticDataInstances = count;

        // add a null value for each data field to ensure that the temporary size of data fields matches those from file
        int fields = in.v32();
        while (fields-- != 0)
            result.dataFields.add(null);
    }

    /**
     * parse T and F
     */
    abstract void typeBlock();

    final void readFields(Pool<?> p) {
        // we have not yet seen a known field
        int ki = 0;
        // we have to count seen auto fields
        int af = -1;

        // we pass the size by adding null's for each expected field in the stream because AL.clear does not return
        // its backing array, i.e. we will likely not resize it that way
        int idx = p.dataFields.size();

        p.dataFields.clear();
        while (0 != idx--) {
            // read field
            final String name = Strings.r(in);
            FieldType<?> t = fieldType();
            HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
            FieldDeclaration<?, ?> f = null;

            String kfn;

            while (null != (kfn = p.KFN(ki))) {
                // is it the next known field?
                if (name == kfn) {
                    if ((f = p.KFC(ki++, TBN, af, nextFieldID++)) instanceof AutoField)
                        throw new ParseException(in, null,
                                "File contains a field conflicting with transient field " + p.name + "." + name);

                    break;
                }

                // else, it might be an unknown field
                if (name.compareTo(kfn) < 0) {
                    // create unknown field
                    f = new LazyField<>(t, name, nextFieldID++, p);
                    break;
                }

                // else, it is a known fields not contained in the file
                Strings.add(kfn);

                if (p.KFC(ki++, TBN, af, nextFieldID) instanceof AutoField)
                    af--;
                else
                    nextFieldID++;
            }

            if (null == f) {
                // no known fields left, so it is obviously unknown
                f = new LazyField<>(t, name, nextFieldID++, p);
            }

            f.addRestriction(rest);

            fields.add(f);
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    abstract void processData();
}
