package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;

import de.ust.skill.common.java.api.SkillException;
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
import de.ust.skill.common.java.internal.fieldTypes.StringType;
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
 * The parser implementation is based on the denotational semantics given in
 * TR14§6.
 *
 * @author Timm Felden
 * @param <State>
 *            the specific state used by the parser
 */
public abstract class FileParser<State extends SkillState> {
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
    protected final ArrayList<StoragePool<?, ?>> types = new ArrayList<>();
    protected final HashMap<String, StoragePool<?, ?>> poolByName = new HashMap<>();
    protected final Annotation Annotation;
    protected final StringType StringType;

    /**
     * creates a new storage pool of matching name
     * 
     * @note implementation depends heavily on the specification
     */
    protected abstract <T extends B, B extends SkillObject> StoragePool<T, B> newPool(String name,
            StoragePool<? super T, B> superPool, HashSet<TypeRestriction> restrictions);

    protected FileParser(FileInputStream in) {
        this.in = in;
        Strings = new StringPool(in);
        StringType = new StringType(Strings);
        Annotation = new Annotation(types);
    }

    final protected void stringBlock() throws ParseException {
        try {
            int count = (int) in.v64();

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

    // deferred pool resize requests
    private final LinkedList<StoragePool<?, ?>> resizeQueue = new LinkedList<>();
    // pool ⇒ local field count
    private final ArrayList<LFEntry> localFields = new ArrayList<>();

    // field data updates: pool x fieldID
    private final LinkedList<DataEntry> fieldDataQueue = new LinkedList<>();

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
     * Turns a field type into a preliminary type information. In case of user
     * types, the declaration of the respective user type may follow after the
     * field declaration.
     */
    FieldType<?> fieldType() {
        final int typeID = (int) in.v64();
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
            return StringType;
        case 15:
            return new ConstantLengthArray<>(in.v64(), fieldType());
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
        for (int i = (int) in.v64(); i != 0; i--) {
            final int id = (int) in.v64();
            switch (id) {
            case 0:
                // Unique
            case 1:
                // Singleton
            case 2:
                // Monotone
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
        for (int count = (int) in.v64(); count != 0; count--) {
            final int id = (int) in.v64();
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
                    in.v64();
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
                // TODO cod0ing
                // string.get
                in.v64();
                break;
            }

            case 7: {
                // TODO CLP
                break;
            }

            case 9: {
                for (int c = (int) in.v64(); c != 0; c--) {
                    // type IDs
                    in.v64();
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
            final String n = Strings.get(in.v64());
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
            long count = in.v64();

            StoragePool<T, B> definition = null;
            if (poolByName.containsKey(name)) {
                definition = (StoragePool<T, B>) poolByName.get(name);
            } else {
                // restrictions
                final HashSet<TypeRestriction> rest = typeRestrictions();
                // super
                final StoragePool<? super T, B> superDef;
                {
                    final int superID = (int) in.v64();
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
                definition = newPool(name, superDef, rest);
            }
            if (blockIDBarrier < definition.typeID)
                blockIDBarrier = definition.typeID;
            else
                throw new ParseException(in, blockCounter, null,
                        "Found unordered type block. Type %s has id %i, barrier was %i.", name, definition.typeID,
                        blockIDBarrier);

            final long bpo = definition.basePool.data.length
                    + ((0L != count && null != definition.superPool) ? in.v64() : 0L);

            // store block info and prepare resize
            definition.blocks.add(new Block(bpo, count));
            resizeQueue.add(definition);

            localFields.add(new LFEntry(definition, (int) in.v64()));
        } catch (java.nio.BufferUnderflowException e) {
            throw new ParseException(in, blockCounter, e, "unexpected end of file");
        }
    }

    final protected void typeBlock() {
        // reset counters and queues
        seenTypes.clear();
        blockIDBarrier = 0;
        resizeQueue.clear();
        localFields.clear();
        fieldDataQueue.clear();
        offset = 0L;

        // parse type
        for (int count = (int) in.v64(); count != 0; count--)
            typeDefinition();

        // resize pools
        {
            Stack<StoragePool<?, ?>> resizeStack = new Stack<>();

            // resize base pools and push entries to stack
            for (StoragePool<?, ?> p : resizeQueue) {
                if (p instanceof BasePool<?>) {
                    ((BasePool<?>) p).resizeData();
                }
                resizeStack.push(p);
            }

            // create instances from stack
            while (!resizeStack.isEmpty()) {
                resizeStack.pop().insertInstances();
            }
        }

        // parse fields
        for (LFEntry lfe : localFields) {
            final StoragePool<?, ?> p = lfe.p;

            // read field part
            int legalFieldIDBarrier = 1 + p.dataFields.size();

            final Block lastBlock = p.blocks.get(p.blocks.size() - 1);

            for (int fieldCounter = lfe.count; fieldCounter != 0; fieldCounter--) {
                final int ID = (int) in.v64();
                if (ID > legalFieldIDBarrier || ID <= 0)
                    throw new ParseException(in, blockCounter, null, "Found an illegal field ID: %d", ID);

                final long end;
                if (ID == legalFieldIDBarrier) {
                    // new field
                    final String fieldName = Strings.get(in.v64());
                    if (null == fieldName)
                        throw new ParseException(in, blockCounter, null, "corrupted file: nullptr in fieldname");

                    FieldType<?> t = fieldType();
                    HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
                    end = in.v64();

                    try {
                        p.addField(ID, t, fieldName, rest).addChunk(new BulkChunk(offset, end, p.size()));
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
     * helper for pool creation in generated code; optimization for all pools
     * that do not have auto fields
     */
    @SuppressWarnings("unchecked")
    protected static <T extends SkillObject> AutoField<?, T>[] noAutoFields() {
        return (AutoField<?, T>[]) StoragePool.noAutoFields;
    }
}
