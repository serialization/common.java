package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import de.ust.skill.common.java.internal.FieldTypes.Annotation;
import de.ust.skill.common.java.internal.FieldTypes.BoolType;
import de.ust.skill.common.java.internal.FieldTypes.ConstantI16;
import de.ust.skill.common.java.internal.FieldTypes.ConstantI32;
import de.ust.skill.common.java.internal.FieldTypes.ConstantI64;
import de.ust.skill.common.java.internal.FieldTypes.ConstantI8;
import de.ust.skill.common.java.internal.FieldTypes.ConstantLengthArray;
import de.ust.skill.common.java.internal.FieldTypes.ConstantV64;
import de.ust.skill.common.java.internal.FieldTypes.F32;
import de.ust.skill.common.java.internal.FieldTypes.F64;
import de.ust.skill.common.java.internal.FieldTypes.I16;
import de.ust.skill.common.java.internal.FieldTypes.I32;
import de.ust.skill.common.java.internal.FieldTypes.I64;
import de.ust.skill.common.java.internal.FieldTypes.I8;
import de.ust.skill.common.java.internal.FieldTypes.ListType;
import de.ust.skill.common.java.internal.FieldTypes.MapType;
import de.ust.skill.common.java.internal.FieldTypes.SetType;
import de.ust.skill.common.java.internal.FieldTypes.StringType;
import de.ust.skill.common.java.internal.FieldTypes.V64;
import de.ust.skill.common.java.internal.FieldTypes.VariableLengthArray;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.java.restrictions.NonNull;
import de.ust.skill.common.java.restrictions.TypeRestriction;
import de.ust.skill.common.jvm.streams.FileInputStream;

/**
 * The parser implementation is based on the denotational semantics given in
 * TR14§6.
 *
 * @author Timm Felden
 */
public abstract class FileParser<State extends SkillState> {
    private FileInputStream in;

    // ERROR REPORTING
    protected int blockCounter = 0;
    protected HashSet<String> seenTypes = new HashSet<>();

    // strings
    final StringPool Strings;

    // types
    final ArrayList<StoragePool<?, ?>> types = new ArrayList<>();
    final HashMap<String, StoragePool<?, ?>> poolByName = new HashMap<>();
    final Annotation Annotation = new Annotation(types);
    final StringType StringType;

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
    // deferred field declaration appends: pool, ID, type, name, block
    private final LinkedList<InsertionEntry> fieldInsertionQueue = new LinkedList<>();

    private final static class InsertionEntry {
        public InsertionEntry(StoragePool<?, ?> owner, int ID, FieldType<?> t, HashSet<FieldRestriction<?>> rest,
                String name, BulkChunk bulkChunkInfo) {
            this.owner = owner;
            // TODO Auto-generated constructor stub
            this.ID = ID;
            type = t;
            restrictions = rest;
            this.name = name;
            bci = bulkChunkInfo;
        }

        final StoragePool<?, ?> owner;
        final int ID;
        final HashSet<FieldRestriction<?>> restrictions;
        final FieldType<?> type;
        final String name;
        final BulkChunk bci;
    }

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
                return new TypeDefinitionIndex<>(typeID - 32);

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

            case 0:
                // TODO check that t is a reference type
                rval.add(NonNull.get());
                break;

            case 3:
                // TODO provide translation
                // t match {
                // case I8 ⇒ restrictions.Range(in.i8, in.i8)
                // case I16 ⇒ restrictions.Range(in.i16, in.i16)
                // case I32 ⇒ restrictions.Range(in.i32, in.i32)
                // case I64 ⇒ restrictions.Range(in.i64, in.i64)
                // case V64 ⇒ restrictions.Range(in.v64, in.v64)
                // case F32 ⇒ restrictions.Range(in.f32, in.f32)
                // case F64 ⇒ restrictions.Range(in.f64, in.f64)
                // case t ⇒ throw new ParseException(in, blockCounter,
                // s"Type $t can not be range restricted!", null)
                // }
            case 5:
                // case 5 ⇒ Coding(String.get(in.v64))
            case 7:
                // case 7 ⇒ ConstantLengthPointer
            default:
                if (id <= 9 || 1 == (id % 2))
                    throw new ParseException(in, blockCounter, null,
                            "Found unknown field restriction %d. Please regenerate your binding, if possible.", id);
                System.err
                        .println("Skipped unknown skippable type restriction. Please update the SKilL implementation.");
            }
        }
        return rval;
    }

    @SuppressWarnings("unchecked")
    private <B extends SkillObject, T extends B> void typeDefinition() {
        // read type part
        final String name = Strings.get(in.v64());
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
                        throw new ParseException(
                                in,
                                blockCounter,
                                null,
                                "Type %s refers to an ill-formed super type.\n          found: %d; current number of other types %d",
                                name, superID, types.size());
                    else
                        superDef = (StoragePool<? super T, B>) types.get(superID - 1);
                }

                // allocate pool
                definition = newPool(name, superDef, rest);
            }

            final long bpo = definition.basePool.data.length
                    + ((0L != count && null != definition.superPool) ? in.v64() : 0L);

            // store block info and prepare resize
            definition.blocks.add(new Block(bpo, count));
            resizeQueue.add(definition);

            // read field part
            final ArrayList<FieldDeclaration<?, T>> fields = definition.fields;
            int totalFieldCount = fields.size();

            final int localFieldCount = (int) in.v64();
            for (int fieldCounter = 0; fieldCounter < localFieldCount; fieldCounter++) {
                final int ID = (int) in.v64();
                if (ID > totalFieldCount || ID < 0)
                    throw new ParseException(in, blockCounter, null, "Found an illegal field ID: %i", ID);

                final long end;
                if (ID == totalFieldCount) {
                    // new field
                    final String fieldName = Strings.get(in.v64());
                    if (null == fieldName)
                        throw new ParseException(in, blockCounter, null, "corrupted file: nullptr in fieldname");

                    FieldType<?> t = fieldType();
                    HashSet<FieldRestriction<?>> rest = fieldRestrictions(t);
                    end = in.v64();

                    fieldInsertionQueue.add(new InsertionEntry(definition, ID, t, rest, name, new BulkChunk(offset,
                            end, count + definition.size())));
                    totalFieldCount += 1;

                } else {
                    // field already seen
                    end = in.v64();
                    fields.get(ID).addChunk(new SimpleChunk(offset, end, bpo, count));

                }
                offset = end;
                fieldDataQueue.add(new DataEntry(definition, ID));
            }
        } catch (java.nio.BufferUnderflowException e) {
            throw new ParseException(in, blockCounter, e, "unexpected end of file");
        }
    }

    protected void typeBlock() {
        // reset fields
        resizeQueue.clear();
        fieldInsertionQueue.clear();
        fieldDataQueue.clear();
        offset = 0L;

        // parse block
        for (int count = (int) in.v64(); count != 0; count--)
            typeDefinition();

        // update status
        throw new Error("todo");
        // resizePools();
        // insertFields();
        // processFieldData();
    }

    // static {
    //
    //
    // @inline def typeDefinition[T <: B, B <: SkillType]
    // @inline def resizePools {
    // val resizeStack = new Stack[StoragePool[_ <: SkillType, _ <: SkillType]]
    // // resize base pools and push entries to stack
    // for (p ← resizeQueue) {
    // p match {
    // case p : BasePool[_] ⇒ p.resizeData(p.blockInfos.last.count.toInt)
    // case _ ⇒
    // }
    // resizeStack.push(p)
    // }
    //
    // // create instances from stack
    // for (p ← resizeStack) {
    // val bi = p.blockInfos.last
    // var i = bi.bpo
    // val high = bi.bpo + bi.count
    // while (i < high && p.insertInstance(i + 1))
    // i += 1;
    // }
    // }
    // @inline def insertFields {
    // for ((p, id, t, rs, name, block) ← fieldInsertionQueue) {
    // p.addField(id, eliminatePreliminaryTypesIn(t), name, rs).addChunk(block)
    // }
    // }
    // @inline def eliminatePreliminaryTypesIn[T](t : FieldType[T]) :
    // FieldType[T] = t match {
    // case TypeDefinitionIndex(i) ⇒ try {
    // types(i.toInt).asInstanceOf[FieldType[T]]
    // } catch {
    // case e : Exception ⇒ throw ParseException(in, blockCounter,
    // s"inexistent user type $i (user types: ${
    // types.zipWithIndex.map(_.swap).toMap.mkString
    // })", e)
    // }
    // case TypeDefinitionName(n) ⇒ try {
    // poolByName(n).asInstanceOf[FieldType[T]]
    // } catch {
    // case e : Exception ⇒ throw ParseException(in, blockCounter,
    // s"inexistent user type $n (user types: ${poolByName.mkString})", e)
    // }
    // case ConstantLengthArray(l, t) ⇒ ConstantLengthArray(l,
    // eliminatePreliminaryTypesIn(t))
    // case VariableLengthArray(t) ⇒
    // VariableLengthArray(eliminatePreliminaryTypesIn(t))
    // case ListType(t) ⇒ ListType(eliminatePreliminaryTypesIn(t))
    // case SetType(t) ⇒ SetType(eliminatePreliminaryTypesIn(t))
    // case MapType(k, v) ⇒ MapType(eliminatePreliminaryTypesIn(k),
    // eliminatePreliminaryTypesIn(v))
    // case t ⇒ t
    // }
    // @inline def processFieldData {
    // // we have to add the file offset to all begins and ends we encounter
    // val fileOffset = in.position
    // var dataEnd = fileOffset
    //
    // // awaiting async read operations
    // val asyncReads = ArrayBuffer[Future[Try[Unit]]]();
    //
    // //process field data declarations in order of appearance and update
    // offsets to absolute positions
    // @inline def processField[T](p : StoragePool[_ <: SkillType, _ <:
    // SkillType], index : Int) {
    // val f = p.fields(index).asInstanceOf[FieldDeclaration[T]]
    // f.t = eliminatePreliminaryTypesIn[T](f.t.asInstanceOf[FieldType[T]])
    //
    // // make begin/end absolute
    // f.addOffsetToLastChunk(fileOffset)
    // val last = f.lastChunk
    //
    // val map = in.map(0L, last.begin, last.end)
    // asyncReads.append(Future(Try(try {
    // f.read(map)
    // // map was not consumed
    // if (!map.eof && !(f.isInstanceOf[LazyField[_]] ||
    // f.isInstanceOf[IgnoredField]))
    // throw PoolSizeMissmatchError(blockCounter, last.begin, last.end, f)
    // } catch {
    // case e : BufferUnderflowException ⇒
    // throw PoolSizeMissmatchError(blockCounter, last.begin, last.end, f)
    // }
    // )))
    // dataEnd = Math.max(dataEnd, last.end)
    // }
    // for ((p, fID) ← fieldDataQueue) {
    // processField(p, fID)
    // }
    // in.jump(dataEnd)
    //
    // // await async reads
    // for (f ← asyncReads) {
    // Await.result(f, Duration.Inf) match {
    // case Failure(e) ⇒
    // e.printStackTrace()
    // println("throw")
    // if (e.isInstanceOf[SkillException]) throw e
    // else throw ParseException(in, blockCounter,
    // "unexpected exception while reading field data (see below)", e)
    // case _ ⇒
    // }
    // }
    // }
    // }

}