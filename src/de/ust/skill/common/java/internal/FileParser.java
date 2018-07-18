package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.api.SkillFile.Mode;
import de.ust.skill.common.java.internal.exceptions.InvalidPoolIndexException;
import de.ust.skill.common.java.internal.exceptions.ParseException;
import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.java.internal.fieldTypes.BoolType;
import de.ust.skill.common.java.internal.fieldTypes.ConstantI16;
import de.ust.skill.common.java.internal.fieldTypes.ConstantI32;
import de.ust.skill.common.java.internal.fieldTypes.ConstantI64;
import de.ust.skill.common.java.internal.fieldTypes.ConstantI8;
import de.ust.skill.common.java.internal.fieldTypes.ConstantLengthArray;
import de.ust.skill.common.java.internal.fieldTypes.ConstantV64;
import de.ust.skill.common.java.internal.fieldTypes.F32;
import de.ust.skill.common.java.internal.fieldTypes.F64;
import de.ust.skill.common.java.internal.fieldTypes.I16;
import de.ust.skill.common.java.internal.fieldTypes.I32;
import de.ust.skill.common.java.internal.fieldTypes.I64;
import de.ust.skill.common.java.internal.fieldTypes.I8;
import de.ust.skill.common.java.internal.fieldTypes.ListType;
import de.ust.skill.common.java.internal.fieldTypes.MapType;
import de.ust.skill.common.java.internal.fieldTypes.ReferenceType;
import de.ust.skill.common.java.internal.fieldTypes.SetType;
import de.ust.skill.common.java.internal.fieldTypes.V64;
import de.ust.skill.common.java.internal.fieldTypes.VariableLengthArray;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.java.restrictions.NonNull;
import de.ust.skill.common.java.restrictions.Range;
import de.ust.skill.common.java.restrictions.TypeRestriction;
import de.ust.skill.common.jvm.streams.FileInputStream;

/**
 * The parser implementation is based on the denotational semantics given in TR14§6.
 *
 * @author Timm Felden
 */
public abstract class FileParser {
    private static final class LFEntry {
        public final StoragePool<?, ?> p;
        public final int count;

        public LFEntry(StoragePool<?, ?> p, int count) {
            this.p = p;
            this.count = count;
        }
    }

    protected FileInputStream in;

    // ERROR REPORTING
    protected int blockCounter = 0;
    protected HashSet<String> seenTypes = new HashSet<>();

    // this barrier is strictly increasing inside of each block and reset to 0
    // at the beginning of each block
    protected int blockIDBarrier = 0;

    // strings
    protected final StringPool Strings;

    // types
    protected final ArrayList<StoragePool<?, ?>> types;
    protected final HashMap<String, StoragePool<?, ?>> poolByName = new HashMap<>();
    protected final Annotation Annotation;

    /**
     * creates a new storage pool of matching name
     * 
     * @note implementation depends heavily on the specification
     */
    protected abstract <T extends B, B extends SkillObject> StoragePool<T, B> newPool(String name,
            StoragePool<? super T, B> superPool, HashSet<TypeRestriction> restrictions);

    protected FileParser(FileInputStream in, int IRSize) {
        types = new ArrayList<>(IRSize);
        this.in = in;
        Strings = new StringPool(in);
        Annotation = new Annotation(types);

        // parse blocks
        while (!in.eof()) {
            stringBlock();
            typeBlock();
        }
    }

    final protected void stringBlock() throws ParseException {
        try {
            int count = in.v32();

            if (0 != count) {
                // read offsets
                int[] offsets = new int[count];
                for (int i = 0; i < count; i++) {
                    offsets[i] = in.i32();
                }

                // store offsets
                int last = 0;
                for (int i = 0; i < count; i++) {
                    Strings.stringPositions.add(new StringPool.Position(in.position() + last, offsets[i] - last));
                    Strings.idMap.add(null);
                    last = offsets[i];
                }
                in.jump(in.position() + last);
            }
        } catch (Exception e) {
            throw new ParseException(in, blockCounter, e, "corrupted string block");
        }
    }

    // pool ⇒ local field count
    private final ArrayList<LFEntry> localFields = new ArrayList<>();

    // field data updates: pool x fieldID
    private final ArrayList<DataEntry> fieldDataQueue = new ArrayList<>();

    private final static class DataEntry {
        public DataEntry(StoragePool<?, ?> owner, int fieldID) {
            this.owner = owner;
            this.fieldID = fieldID;
        }

        final StoragePool<?, ?> owner;
        final int fieldID;
    }

    private long offset = 0L;

    /**
     * Turns a field type into a preliminary type information. In case of user types, the declaration of the respective
     * user type may follow after the field declaration.
     */
    FieldType<?> fieldType() {
        final int typeID = in.v32();
        switch (typeID) {
        case 0:
            return new ConstantI8(in.i8());
        case 1:
            return new ConstantI16(in.i16());
        case 2:
            return new ConstantI32(in.i32());
        case 3:
            return new ConstantI64(in.i64());
        case 4:
            return new ConstantV64(in.v64());
        case 5:
            return Annotation;
        case 6:
            return BoolType.get();
        case 7:
            return I8.get();
        case 8:
            return I16.get();
        case 9:
            return I32.get();
        case 10:
            return I64.get();
        case 11:
            return V64.get();
        case 12:
            return F32.get();
        case 13:
            return F64.get();
        case 14:
            return Strings;
        case 15:
            return new ConstantLengthArray<>(in.v32(), fieldType());
        case 17:
            return new VariableLengthArray<>(fieldType());
        case 18:
            return new ListType<>(fieldType());
        case 19:
            return new SetType<>(fieldType());
        case 20:
            return new MapType<>(fieldType(), fieldType());
        default:
            if (typeID >= 32)
                return types.get(typeID - 32);

            throw new ParseException(in, blockCounter, null, "Invalid type ID: %d", typeID);
        }
    }

    private HashSet<TypeRestriction> typeRestrictions() {
        final HashSet<TypeRestriction> rval = new HashSet<>();
        // parse count many entries
        for (int i = in.v32(); i != 0; i--) {
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
                    throw new ParseException(in, blockCounter, null,
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
                if (t instanceof ReferenceType)
                    rval.add(NonNull.get());
                else
                    throw new ParseException(in, blockCounter, null, "Nonnull restriction on non-refernce type: %s.",
                            t.toString());
                break;
            }

            case 1: {
                // default
                if (t instanceof ReferenceType) {
                    // TODO typeId -> ref
                    in.v32();
                } else {
                    // TODO other values
                    t.readSingleField(in);
                }
                break;
            }

            case 3: {
                final FieldRestriction<?> r = Range.make(t.typeID, in);
                if (null == r)
                    throw new ParseException(in, blockCounter, null, "Type %s can not be range restricted!",
                            t.toString());
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
                    throw new ParseException(in, blockCounter, null,
                            "Found unknown field restriction %d. Please regenerate your binding, if possible.", id);
                System.err
                        .println("Skipped unknown skippable type restriction. Please update the SKilL implementation.");
                break;
            }
        }
        return rval;
    }

    @SuppressWarnings("unchecked")
    private <B extends SkillObject, T extends B> void typeDefinition() {
        // read type part
        final String name;
        try {
            final String n = Strings.get(in.v32());
            name = n;
        } catch (InvalidPoolIndexException e) {
            throw new ParseException(in, blockCounter, e, "corrupted type header");
        }
        if (null == name)
            throw new ParseException(in, blockCounter, null, "corrupted file: nullptr in typename");

        // type duplication error detection
        if (seenTypes.contains(name))
            throw new ParseException(in, blockCounter, null, "Duplicate definition of type %s", name);
        seenTypes.add(name);

        // try to parse the type definition
        try {
            int count = in.v32();

            StoragePool<T, B> definition = null;
            if (poolByName.containsKey(name)) {
                definition = (StoragePool<T, B>) poolByName.get(name);
            } else {
                // restrictions
                final HashSet<TypeRestriction> rest = typeRestrictions();
                // super
                final StoragePool<? super T, B> superDef;
                {
                    final int superID = in.v32();
                    if (0 == superID)
                        superDef = null;
                    else if (superID > types.size())
                        throw new ParseException(in, blockCounter, null,
                                "Type %s refers to an ill-formed super type.\n"
                                        + "          found: %d; current number of other types %d",
                                name, superID, types.size());
                    else
                        superDef = (StoragePool<? super T, B>) types.get(superID - 1);
                }

                // allocate pool
                try {
                    definition = newPool(name, superDef, rest);
                    if (definition.superPool != superDef)
                        throw new ParseException(in, blockCounter, null,
                                "The file contains a super type %s but %s is specified to be a base type.",
                                superDef == null ? "<none>" : superDef.name, name);

                    poolByName.put(name, definition);
                } catch (ClassCastException e) {
                    throw new ParseException(in, blockCounter, e,
                            "The super type of %s stored in the file does not match the specification!", name);
                }
            }
            if (blockIDBarrier < definition.typeID)
                blockIDBarrier = definition.typeID;
            else
                throw new ParseException(in, blockCounter, null,
                        "Found unordered type block. Type %s has id %i, barrier was %i.", name, definition.typeID,
                        blockIDBarrier);

            // in contrast to prior implementation, bpo is the position inside
            // of data, even if there are no actual
            // instances. We need this behavior, because that way we can cheaply
            // calculate the number of static instances
            final int bpo = definition.basePool.cachedSize + (null == definition.superPool ? 0
                    : (0 != count ? (int) in.v64() : definition.superPool.lastBlock().bpo));

            // ensure that bpo is in fact inside of the parents block
            if (null != definition.superPool) {
                Block b = definition.superPool.lastBlock();
                if (bpo < b.bpo || b.bpo + b.count < bpo)
                    throw new ParseException(in, blockCounter, null, "Found broken bpo.");
            }

            // static count and cached size are updated in the resize phase
            // @note we assume that all dynamic instance are static instances as
            // well, until we know for sure
            definition.blocks.add(new Block(bpo, count, count));
            definition.staticDataInstances += count;

            localFields.add(new LFEntry(definition, (int) in.v64()));
        } catch (java.nio.BufferUnderflowException e) {
            throw new ParseException(in, blockCounter, e, "unexpected end of file");
        }
    }

    final protected void typeBlock() {
        // reset counters and queues
        seenTypes.clear();
        blockIDBarrier = 0;
        localFields.clear();
        fieldDataQueue.clear();
        offset = 0L;

        // parse type
        for (int count = in.v32(); count != 0; count--)
            typeDefinition();

        // resize pools by updating cachedSize and staticCount
        // @note instances will be allocated just before field deserialization
        for (LFEntry e : localFields) {
            final StoragePool<?, ?> p = e.p;
            final Block b = p.lastBlock();
            p.cachedSize += b.count;

            if (0 != b.count) {
                // calculate static count of our parent
                StoragePool<?, ?> parent = p.superPool;
                if (null != parent) {
                    Block sb = parent.lastBlock();
                    // assumed static instances, minus what static instances
                    // would be, if p were the first sub pool.
                    int delta = sb.staticCount - (b.bpo - sb.bpo);
                    // if positive, then we have to subtract it from the assumed
                    // static count (local and global)
                    if (delta > 0) {
                        sb.staticCount -= delta;
                        parent.staticDataInstances -= delta;
                    }
                }
            }
        }

        // parse fields
        for (LFEntry lfe : localFields) {
            final StoragePool<?, ?> p = lfe.p;

            // read field part
            int legalFieldIDBarrier = 1 + p.dataFields.size();

            final Block lastBlock = p.blocks.get(p.blocks.size() - 1);

            for (int fieldCounter = lfe.count; fieldCounter != 0; fieldCounter--) {
                final int ID = in.v32();
                if (ID > legalFieldIDBarrier || ID <= 0)
                    throw new ParseException(in, blockCounter, null, "Found an illegal field ID: %d", ID);

                final long end;
                if (ID == legalFieldIDBarrier) {
                    // new field
                    final String fieldName = Strings.get(in.v32());
                    if (null == fieldName)
                        throw new ParseException(in, blockCounter, null, "corrupted file: nullptr in fieldname");

                    FieldType<?> t = fieldType();
                    HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
                    end = in.v64();

                    try {
                        // try to allocate simple chunks, because working on
                        // them is cheaper
                        FieldDeclaration<?, ?> f = p.addField(t, fieldName);
                        for (FieldRestriction<?> r : rest)
                            f.addRestriction(r);
                        f.addChunk(1 == p.blocks().size() ? new SimpleChunk(offset, end, lastBlock.bpo, lastBlock.count)
                                : new BulkChunk(offset, end, p.cachedSize, p.blocks().size()));
                    } catch (SkillException e) {
                        // transform to parse exception with propper values
                        throw new ParseException(in, blockCounter, null, e.getMessage());
                    }
                    legalFieldIDBarrier += 1;

                } else {
                    // field already seen
                    end = in.v64();
                    p.dataFields.get(ID - 1).addChunk(new SimpleChunk(offset, end, lastBlock.bpo, lastBlock.count));

                }
                offset = end;
                fieldDataQueue.add(new DataEntry(p, ID));
            }
        }

        processFieldData();
    }

    private final void processFieldData() {
        // we have to add the file offset to all begins and ends we encounter
        final long fileOffset = in.position();
        long dataEnd = fileOffset;

        // process field data declarations in order of appearance and update
        // offsets to absolute positions
        for (DataEntry e : fieldDataQueue) {
            final FieldDeclaration<?, ?> f = e.owner.dataFields.get(e.fieldID - 1);

            // make begin/end absolute
            final long end = f.addOffsetToLastChunk(in, fileOffset);
            dataEnd = Math.max(dataEnd, end);
        }
        in.jump(dataEnd);
    }

    /**
     * helper for pool creation in generated code; optimization for all pools that do not have auto fields
     */
    @SuppressWarnings("unchecked")
    protected static <T extends SkillObject> AutoField<?, T>[] noAutoFields() {
        return (AutoField<?, T>[]) StoragePool.noAutoFields;
    }

    /**
     * creates a matching skill state out of this file parser's state
     */
    public <State extends SkillState> State read(Class<State> cls, Mode writeMode) {
        // the generated state has exactly one constructor
        try {
            @SuppressWarnings("unchecked")
            State r = (State) cls.getConstructors()[0].newInstance(poolByName, Strings, Annotation, types, in,
                    writeMode);

            r.check();
            return r;
        } catch (SkillException e) {
            throw new ParseException(in, blockCounter, e, "Post serialization check failed!");
        } catch (Exception e) {
            throw new ParseException(in, blockCounter, e, "State instantiation failed!");
        }
    }
}
