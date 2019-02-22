package ogss.common.java.internal;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.java.internal.exceptions.PoolSizeMissmatchError;
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
import ogss.common.streams.MappedInStream;

/**
 * The parser implementation is based on the denotational semantics given in TR14§6.
 *
 * @author Timm Felden
 */
public final class Parser extends StateInitializer {

    // the index of the next known class
    private int nextClass;

    /**
     * name of all known classes to distinguish between known and unknown classes from the file spec
     * 
     * @note created on first use
     */
    private HashSet<String> knownNames;

    /**
     * This buffer provides the association of file fieldID to field.
     */
    private ArrayList<Object> fields = new ArrayList<>();

    /**
     * User defined types. This array is used to resolve type IDs while parsing. The type IDs assigned to created
     * entities may not correspond to udts indices (shifted by 10).
     */
    final ArrayList<FieldType<?>> udts = new ArrayList<>();

    // synchronization of field read jobs
    final Semaphore barrier = new Semaphore(0);

    public SkillException readErrors;

    public Parser(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames, KCC[] kccs) {
        super(in, knownClasses, classNames, kccs);

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
    private FieldType<?> fieldType() {
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

    private HashSet<TypeRestriction> typeRestrictions(int i) {
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

    private HashSet<FieldRestriction<?>> fieldRestrictions(FieldType<?> t) {
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

    // TODO remove type arguments?
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <B extends Pointer, T extends B> void typeDefinition() {

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
        final Pool<? super T, B> superDef;
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
                superDef = (Pool<? super T, B>) classes.get(superID - 1);
                bpo = in.v32();
            }
        }

        // allocate pool
        final Pool<T, B> definition;
        while (true) {

            // check common case, i.e. the next class is the expected one
            if (classNames[nextClass] == name) {
                try {
                    definition = (Pool<T, B>) knownClasses[nextClass].getConstructors()[0].newInstance(classes,
                            superDef);
                    SIFA[nextClass] = definition;
                } catch (Exception e) {
                    throw new ParseException(in, e, "Failed to instantiate known class " + name);
                }

                if (definition.superPool != superDef)
                    throw new ParseException(in, null, "Class %s has no super type but the file defines super type %s",
                            name, superDef.name);

                nextClass++;

            } else {
                // ensure that the name has not been used before
                if (typeByName.containsKey(name)) {
                    throw new ParseException(in, null, "Duplicate definition of class " + name);
                }

                // ensure that knownNames is filled with known names
                if (null == knownNames) {
                    knownNames = new HashSet<>(classNames.length * 2);
                    for (String n : classNames)
                        knownNames.add(n);
                }

                final boolean known = knownNames.contains(name);
                if (known) {
                    // the class has a known name, has not been declared before and is not the expected class
                    // therefore, we have to allocate all classes
                    while (classNames[nextClass] != name) {

                        // @note: we do not know, which parameters to pass the constructor; therefore, the generated
                        // constructor will calculate its super type if null is passed as super type
                        Pool<?, ?> p;
                        try {
                            p = (Pool<?, ?>) knownClasses[nextClass].getConstructors()[0].newInstance(classes, null);
                            SIFA[nextClass] = p;
                        } catch (Exception e) {
                            throw new ParseException(in, e,
                                    "Failed to instantiate known class " + classNames[nextClass]);
                        }

                        // note: p will not receive data fields; this is exactly, what we intend here
                        // note: bpo/sizes are not set, because zero-allocation is correct there

                        classes.add(p);
                        typeByName.put(p.name, p);
                        nextClass++;
                    }
                    // the next class is that obtained from file, so jump back to the start of the loop
                    continue;

                }

                // the pool is not known
                final int idx = classes.size();
                if (null == superDef) {
                    definition = new BasePool(idx, name, Pool.myKFN, Pool.myKFC, 0);
                } else {
                    definition = (Pool<T, B>) superDef.makeSubPool(idx, name);
                }
            }
            //
            // hier über nextClass, id, name pools anlegen
            // note: name über reflection!

            udts.add(definition);
            classes.add(definition);
            typeByName.put(name, definition);
            break;
        }

        definition.bpo = bpo;
        definition.cachedSize = definition.staticDataInstances = count;

        // add a null value for each data field to ensure that the temporary size of data fields matches those from file
        int fields = in.v32();
        while (fields-- != 0)
            definition.dataFields.add(null);
    }

    /**
     * parse T and F
     */
    final private void typeBlock() {

        /**
         * *************** * T Class * ****************
         */
        for (int count = in.v32(); count != 0; count--)
            typeDefinition();

        // calculate cached size and next for all pools
        {
            final int cs = classes.size();
            if (0 != cs) {
                int i = cs - 2;
                if (i >= 0) {
                    Pool<?, ?> n, p = classes.get(i + 1);
                    // propagate information in reverse order
                    // i is the pool where next is set, hence we skip the last pool
                    do {
                        n = p;
                        p = classes.get(i);

                        // by compactness, if n has a super pool, p is the previous pool
                        if (null != n.superPool) {
                            // raw cast, because we cannot prove here that it is B, because we do not want to introduce
                            // a function as quantifier which would not provide any benefit anyway
                            p.next = (Pool) n;
                            n.superPool.cachedSize += n.cachedSize;
                            if (0 == n.bpo) {
                                n.bpo = p.bpo;
                            }
                        }

                    } while (--i >= 0);
                }

                // allocate data and start instance allocation jobs
                Pointer[] d = null;
                while (++i < cs) {
                    final Pool<?, ?> p = classes.get(i);
                    if (null == p.superPool) {
                        // create new d, because we are in a new type hierarchy
                        d = new Pointer[p.cachedSize];
                    }
                    p.data = d;
                    if (0 != p.staticDataInstances) {
                        State.pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                p.allocateInstances();
                                barrier.release();
                            }
                        });
                    } else {
                        // we would not allocate an instance anyway
                        barrier.release();
                    }
                }
            }
        }

        /**
         * *************** * T Container * ****************
         */
        {
            // next type ID
            int tid = 10 + classes.size();
            // KCC index
            int ki = 0;
            for (int count = in.v32(); count != 0; count--) {
                final int kind = in.i8();
                final FieldType<?> b1 = fieldType();
                final FieldType<?> b2 = (3 == kind) ? fieldType() : null;
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

                // construct known containers not present in the file
                int cmp;
                while ((cmp = name.compareTo(kccs[ki].name)) > 0) {
                    KCC c = kccs[ki++];
                    HullType<?> r;
                    switch (c.kind) {
                    case 0:
                        r = new ArrayType<>(tid++, typeByName.get(c.b1));
                        break;
                    case 1:
                        r = new ListType<>(tid++, typeByName.get(c.b1));
                        break;
                    case 2:
                        r = new SetType<>(tid++, typeByName.get(c.b1));
                        break;

                    case 3:
                        r = new MapType<>(tid++, typeByName.get(c.b1), typeByName.get(c.b2));
                        break;

                    default:
                        throw new SkillException("Illegal container constructor ID: " + c.kind);
                    }
                    typeByName.put(r.toString(), r);
                    r.fieldID = nextFieldID++;
                    containers.add(r);
                }
                // construct an expected container
                if (0 == cmp) {
                    ki++;
                }
                HullType<?> r;
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

                typeByName.put(r.toString(), r);
                r.fieldID = nextFieldID++;
                fields.add(r);
                udts.add(r);
                containers.add(r);
            }
        }

        /**
         * *************** * T Enum * ****************
         */
        for (int count = in.v32(); count != 0; count--)
            throw new Error("TODO");

        /**
         * *************** * F * ****************
         */
        for (Pool<?, ?> p : classes) {
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

                while (ki < p.knownFields.length) {
                    // is it the next known field?
                    if (name == p.knownFields[ki]) {
                        try {
                            final Class<?> cls = p.KFC[ki++];
                            if (cls.getSuperclass() == AutoField.class)
                                throw new ParseException(in, null,
                                        "File contains a field conflicting with transient field " + p.name + "."
                                                + name);

                            f = (FieldDeclaration<?, ?>) cls.getConstructor(FieldType.class, int.class, p.getClass())
                                    .newInstance(t, nextFieldID++, p);
                        } catch (ParseException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new ParseException(in, e, "Failed to instantiate known field " + p.name + "." + name);
                        }
                        break;
                    }

                    // else, it might be an unknown field
                    if (name.compareTo(p.knownFields[ki]) < 0) {
                        // create unknown field
                        f = new LazyField(t, name, nextFieldID++, p);
                        break;
                    }

                    // else, it is a known fields not contained in the file
                    try {
                        final Class<?> cls = p.KFC[ki++];
                        // auto fields reside in a separate ID range
                        if (cls.getSuperclass() == AutoField.class) {
                            cls.getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName, af--, p);
                        } else {
                            cls.getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName,
                                    nextFieldID++, p);
                        }
                    } catch (Exception e) {
                        throw new ParseException(in, e, "Failed to instantiate known field " + p.name + "." + name);
                    }
                }

                if (null == f) {
                    // no known fields left, so it is obviously unknown
                    f = new LazyField(t, name, nextFieldID++, p);
                }

                f.addRestriction(rest);

                fields.add(f);
            }
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    private final void processData() {

        // we expect one HD-entry per field
        int remaining = fields.size();
        Runnable[] jobs = new Runnable[remaining];

        int awaitHulls = 0;

        while (--remaining >= 0 & !in.eof()) {
            // get size of the block, which is relative to the position after size
            final int size = in.v32() + 2;
            final int position = in.position();
            final int id = in.v32();
            final Object f = fields.get(id);
            // overwrite entry to prevent duplicate read of the same field
            fields.set(id, null);

            final MappedInStream map = in.map(size + position - in.position());

            if (f instanceof HullType<?>) {
                final int count = map.v32();
                final HullType<?> p = (HullType<?>) f;

                // start hull allocation job
                awaitHulls++;
                State.pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        p.allocateInstances(count, map);
                        barrier.release();
                    }
                });

                // create hull read data task except for StringPool which is still lazy per element and eager per offset
                if (!(p instanceof StringPool)) {
                    jobs[id] = new HRT(p);
                }

            } else {
                // create job with adjusted size that corresponds to the * in the specification (i.e. exactly the data)
                jobs[id] = new ReadTask((FieldDeclaration<?, ?>) f, map);
            }
        }

        // await allocations of class and hull types
        try {
            barrier.acquire(classes.size() + awaitHulls);
        } catch (InterruptedException e) {
            throw new SkillException("internal error: unexpected foreign exception", e);
        }

        // start read tasks
        {
            int skips = 0;
            for (Runnable j : jobs) {
                if (null != j)
                    State.pool.execute(j);
                else
                    skips++;
            }
            if (0 != skips)
                barrier.release(skips);
        }

        // TODO start tasks that perform default initialization of fields not obtained from file
    }

    @Override
    public void awaitResults() {
        // await read jobs and throw error if any occurred
        try {
            barrier.acquire(fields.size());
        } catch (InterruptedException e) {
            throw new SkillException("internal error: unexpected foreign exception", e);
        }
        if (null != readErrors)
            throw readErrors;
    }

    private final class ReadTask implements Runnable {
        private final FieldDeclaration<?, ?> f;
        private final MappedInStream map;

        ReadTask(FieldDeclaration<?, ?> f, MappedInStream in) {
            this.f = f;
            this.map = in;
        }

        @Override
        public void run() {
            SkillException ex = null;
            final Pool<?, ?> owner = f.owner;
            final int bpo = owner.bpo;
            final int end = bpo + owner.cachedSize;
            try {
                if (map.eof()) {
                    // TODO default initialization; this is a nop for now in Java
                } else {
                    f.read(bpo, end, map);
                }

                if (!map.eof() && !(f instanceof LazyField<?, ?>))
                    ex = new PoolSizeMissmatchError(map.position(), bpo, end, f);

            } catch (BufferUnderflowException e) {
                ex = new PoolSizeMissmatchError(bpo, end, f, e);
            } catch (SkillException t) {
                ex = t;
            } catch (Throwable t) {
                ex = new SkillException("internal error: unexpected foreign exception", t);
            } finally {
                barrier.release();
                if (null != ex)
                    synchronized (fields) {
                        if (null == readErrors)
                            readErrors = ex;
                        else
                            readErrors.addSuppressed(ex);
                    }
            }
        }
    }

    /**
     * A hull read task. Reads H-Data.
     * 
     * @author Timm Felden
     */
    private final class HRT implements Runnable {
        private final HullType<?> t;

        HRT(HullType<?> t) {
            this.t = t;
        }

        @Override
        public void run() {
            SkillException ex = null;
            try {
                t.read();
            } catch (SkillException t) {
                ex = t;
            } catch (Throwable t) {
                ex = new SkillException("internal error: unexpected foreign exception", t);
            } finally {
                barrier.release();
                if (null != ex)
                    synchronized (fields) {
                        if (null == readErrors)
                            readErrors = ex;
                        else
                            readErrors.addSuppressed(ex);
                    }
            }
        }
    }
}
