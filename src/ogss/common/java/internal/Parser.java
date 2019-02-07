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

    // synchronization of field read jobs
    final Semaphore barrier = new Semaphore(0);

    public SkillException readErrors;

    public Parser(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames) {
        super(in, knownClasses, classNames);

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
        stringBlock();

        // T
        typeBlock();

        // HD
        processData();

        if (!in.eof()) {
            throw new ParseException(in, null, "Expected end of file, but some bytes remain.");
        }
    }

    final private void stringBlock() throws ParseException {
        fields.add(Strings);

        try {
            int count = in.v32();

            if (0 != count) {
                // read offsets
                int last = 0;
                int[] offsets = new int[count];
                for (int i = 0; i < count; i++) {
                    last += in.v32();
                    offsets[i] = last;
                }

                // store offsets
                // @note this has to be done after reading all offsets, as sizes are relative to that point and decoding
                // is done using absolute sizes
                last = 0;
                for (int i = 0; i < count; i++) {
                    Strings.stringPositions.add(new StringPool.Position(in.position() + last, offsets[i] - last));
                    Strings.idMap.add(null);
                    last = offsets[i];
                }
                in.jump(in.position() + last);
            }
        } catch (Exception e) {
            throw new ParseException(in, e, "corrupted string block");
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
            return Strings;
        case 9:
            return Annotation;
        default:
            return classes.get(typeID - 10);
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
                if (id <= 5 || 1 == (id % 2))
                    throw new ParseException(in, null,
                            "Found unknown type restriction %d. Please regenerate your binding, if possible.", id);
                System.err
                        .println("Skiped unknown skippable type restriction. Please update the SKilL implementation.");
                break;
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
                if (id <= 9 || 1 == (id % 2))
                    throw new ParseException(in, null,
                            "Found unknown field restriction %d. Please regenerate your binding, if possible.", id);
                System.err
                        .println("Skipped unknown skippable type restriction. Please update the SKilL implementation.");
                break;
            }
        }
        return rval;
    }

    // TODO remove type arguments?
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <B extends Pointer, T extends B> void typeDefinition() {

        // name
        final String name = Strings.read(in);
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
                    definition = new BasePool(idx, name, Pool.noKnownFields, Pool.noKFC, Pool.noAutoFields);
                } else {
                    definition = (Pool<T, B>) superDef.makeSubPool(idx, name);
                }
            }
            //
            // hier über nextClass, id, name pools anlegen
            // note: name über reflection!

            classes.add(definition);
            typeByName.put(name, definition);
            break;
        }

        definition.bpo = bpo;
        definition.cachedSize = definition.staticDataInstances = count;

        // TODO begin to allocate instances in parallel

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
                    final Pool p = classes.get(i);
                    if (null == p.superPool) {
                        // create new d, because we are in a new type hierarchy
                        d = new Pointer[p.cachedSize];
                    }
                    p.data = d;
                    State.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            p.allocateInstances();
                            barrier.release();
                        }
                    });
                }
            }
        }

        /**
         * *************** * T Container * ****************
         */
        for (int count = in.v32(); count != 0; count--)
            throw new Error("TODO");

        /**
         * *************** * T Enum * ****************
         */
        for (int count = in.v32(); count != 0; count--)
            throw new Error("TODO");

        /**
         * *************** * F * ****************
         */
        for (Pool<?, ?> p : classes) {
            // TODO change this to match my slides
            // TODO create known fields

            // we have not yet seen a known field
            int ki = 0;

            // we pass the size by adding null's for each expected field in the stream because AL.clear does not return
            // its backing array, i.e. we will likely not resize it that way
            int idx = p.dataFields.size();

            p.dataFields.clear();
            while (0 != idx--) {
                // read field
                final String name = Strings.read(in);
                FieldType<?> t = fieldType();
                HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
                FieldDeclaration<?, ?> f;

                if (ki < p.knownFields.length) {
                    do {
                        // is it the next known field?
                        if (name == p.knownFields[ki]) {
                            try {
                                f = p.KFC[ki++].getConstructor(FieldType.class, int.class, p.getClass()).newInstance(t,
                                        nextFieldID++, p);
                            } catch (Exception e) {
                                throw new ParseException(in, e,
                                        "Failed to instantiate known field " + p.name + "." + name);
                            }
                            break;
                        }

                        // else, it might be an unknown field
                        if (name.compareTo(p.knownFields[ki]) < 0) {
                            // create unknown field
                            f = new LazyField(t, name, nextFieldID++, p);
                            break;
                        }

                        // TODO else, it is a known fields not contained in the file
                        try {
                            p.KFC[ki++].getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName,
                                    nextFieldID++, p);
                        } catch (Exception e) {
                            throw new ParseException(in, e, "Failed to instantiate known field " + p.name + "." + name);
                        }
                        throw new Error("TODO create known field not in file");

                    } while (++ki < p.knownFields.length);
                } else {
                    // no known fields left, so it is obviously unknown
                    f = new LazyField(t, name, nextFieldID++, p);
                }

                f.addRestriction(rest);

                // TODO check against auto field? (or specify that it would no longer be an error)

                fields.add(f);
            }
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    private final void processData() {

        // we have to add the file offset to all begins and ends we encounter
        final long fileOffset = in.position();
        long dataEnd = fileOffset;

        // we expect one HD-entry per field
        int remaining = fields.size();
        ReadTask[] jobs = new ReadTask[remaining];

        int awaitHulls = 0;

        while (--remaining >= 0 & !in.eof()) {
            // get size of the block, which is relative to the position after size
            final int size = in.v32();
            final int position = in.position();
            final int id = in.v32();
            final Object f = fields.get(id);
            // overwrite entry to prevent duplicate read of the same field
            fields.set(id, null);

            if (f instanceof HullType<?>) {
                final int count = in.v32();
                final HullType<?> p = (HullType<?>) f;
                final MappedInStream map = in.map(size + position - in.position());

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
                    // TODO jobs[id] = new HRT(p);
                }

            } else {
                // create job with adjusted size that corresponds to the * in the specification (i.e. exactly the data)
                jobs[id] = new ReadTask((FieldDeclaration<?, ?>) f, in.map(size + position - in.position()));
            }
        }

        // await allocations of class and hull types
        try {
            barrier.acquire(classes.size() + awaitHulls);
        } catch (InterruptedException e) {
            throw new SkillException("internal error: unexpected foreign exception", e);
        }

        // start read tasks
        for (ReadTask j : jobs) {
            if (null != j)
                State.pool.execute(j);
            else
                barrier.release();
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
}
