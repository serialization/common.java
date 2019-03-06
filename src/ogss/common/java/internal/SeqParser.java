package ogss.common.java.internal;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.exceptions.PoolSizeMissmatchError;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.MappedInStream;

/**
 * The sequential version of Parser.
 *
 * @author Timm Felden
 */
public final class SeqParser extends Parser {

    SeqParser(FileInputStream in, int sifaSize, PoolBuilder pb, KCC[] kccs) throws IOException {
        super(in, sifaSize, pb, kccs);
    }

    /**
     * parse T and F
     */
    @Override
    final void typeBlock() {

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
                    Pool<?> n, p = classes.get(i + 1);
                    // propagate information in reverse order
                    // i is the pool where next is set, hence we skip the last pool
                    do {
                        n = p;
                        p = classes.get(i);

                        // by compactness, if n has a super pool, p is the previous pool
                        if (null != n.superPool) {
                            // raw cast, because we cannot prove here that it is B, because we do not want to introduce
                            // a function as quantifier which would not provide any benefit anyway
                            p.next = n;
                            n.superPool.cachedSize += n.cachedSize;
                            if (0 == n.bpo) {
                                n.bpo = p.bpo;
                            }
                        }

                    } while (--i >= 0);
                }

                // allocate data and start instance allocation jobs
                Obj[] d = null;
                while (++i < cs) {
                    final Pool<?> p = classes.get(i);
                    if (null == p.superPool) {
                        // create new d, because we are in a new type hierarchy
                        d = new Obj[p.cachedSize];
                    }
                    p.data = d;
                    if (0 != p.staticDataInstances) {
                        p.allocateInstances();
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
                {
                    int cmp = -1;
                    while (ki < kccs.length && (cmp = name.compareTo(kccs[ki].name)) > 0) {
                        KCC c = kccs[ki++];
                        HullType<?> r;
                        switch (c.kind) {
                        case 0:
                            r = new ArrayType<>(tid++, TBN.get(c.b1));
                            break;
                        case 1:
                            r = new ListType<>(tid++, TBN.get(c.b1));
                            break;
                        case 2:
                            r = new SetType<>(tid++, TBN.get(c.b1));
                            break;

                        case 3:
                            r = new MapType<>(tid++, TBN.get(c.b1), TBN.get(c.b2));
                            break;

                        default:
                            throw new SkillException("Illegal container constructor ID: " + c.kind);
                        }
                        TBN.put(r.toString(), r);
                        r.fieldID = nextFieldID++;
                        containers.add(r);
                    }
                    // construct an expected container
                    if (0 == cmp) {
                        ki++;
                    }
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

                TBN.put(r.toString(), r);
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
        for (Pool<?> p : classes) {
            readFields(p);
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    @Override
    final void processData() {

        // we expect one HD-entry per field
        int remaining = fields.size();
        Object[] jobs = new Object[remaining];

        while (--remaining >= 0 & !in.eof()) {
            // create the map directly and use it for subsequent read-operations to avoid costly position and size
            // readjustments
            final MappedInStream map = in.map(in.v32() + 2);

            final int id = map.v32();
            final Object f = fields.get(id);
            // overwrite entry to prevent duplicate read of the same field
            fields.set(id, null);

            if (f instanceof HullType<?>) {
                final int count = map.v32();
                final HullType<?> p = (HullType<?>) f;

                // start hull allocation job
                p.allocateInstances(count, map);

                // create hull read data task except for StringPool which is still lazy per element and eager per offset
                if (!(p instanceof StringPool)) {
                    jobs[id] = p;
                }

            } else {
                // create job with adjusted size that corresponds to the * in the specification (i.e. exactly the data)
                jobs[id] = new ReadTask((FieldDeclaration<?, ?>) f, map);
            }
        }

        // perform read tasks
        try {
            for (Object j : jobs) {
                if (null != j) {
                    if (j instanceof ReadTask)
                        ((ReadTask) j).run();
                    else {
                        ((HullType<?>) j).read();
                    }
                }
            }
        } catch (SkillException t) {
            throw t;
        } catch (Throwable t) {
            throw new SkillException("internal error: unexpected foreign exception", t);
        }

        // TODO start tasks that perform default initialization of fields not obtained from file
    }

    @Override
    public void awaitResults() {
        // nothing to await
    }

    private final class ReadTask {
        private final FieldDeclaration<?, ?> f;
        private final MappedInStream map;

        ReadTask(FieldDeclaration<?, ?> f, MappedInStream in) {
            this.f = f;
            this.map = in;
        }

        void run() {
            final Pool<?> owner = f.owner;
            final int bpo = owner.bpo;
            final int end = bpo + owner.cachedSize;
            try {
                if (map.eof()) {
                    // TODO default initialization; this is a nop for now in Java
                } else {
                    f.read(bpo, end, map);
                }

                if (!map.eof() && !(f instanceof LazyField<?, ?>))
                    throw new PoolSizeMissmatchError(map.position(), bpo, end, f);

            } catch (BufferUnderflowException e) {
                throw new PoolSizeMissmatchError(bpo, end, f, e);
            }
        }
    }
}
