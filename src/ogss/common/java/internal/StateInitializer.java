package ogss.common.java.internal;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.file.Path;
import java.util.ArrayList;

import ogss.common.java.api.Mode;
import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.fieldTypes.BoolType;
import ogss.common.java.internal.fieldTypes.F32;
import ogss.common.java.internal.fieldTypes.F64;
import ogss.common.java.internal.fieldTypes.I16;
import ogss.common.java.internal.fieldTypes.I32;
import ogss.common.java.internal.fieldTypes.I64;
import ogss.common.java.internal.fieldTypes.I8;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SingleArgumentType;
import ogss.common.java.internal.fieldTypes.V64;
import ogss.common.streams.FileInputStream;

/**
 * Initializes a state. One of Creator, Parser, SequentialParser.
 * 
 * @author Timm Felden
 */
public abstract class StateInitializer {

    public static StateInitializer make(Path path, PoolBuilder pb, Mode... mode) throws IOException {
        final StateInitializer init;
        ActualMode modes = new ActualMode(mode);
        if (modes.create)
            init = new Creator(pb);
        else {
            FileInputStream fs = FileInputStream.open(path);
            try {
                if (fs.size() < Parser.SEQ_LIMIT)
                    init = new SeqParser(fs, pb);
                else
                    init = new ParParser(fs, pb);
            } catch (BufferUnderflowException e) {
                throw new OGSSException("unexpected EOF", e);
            }
        }
        init.path = path;
        init.canWrite = modes.write;
        return init;
    }

    Path path;
    final FileInputStream in;
    boolean canWrite;

    // guard from file
    String guard;

    // strings
    final StringPool Strings;

    // types
    final ArrayList<Pool<?>> classes;
    final ArrayList<HullType<?>> containers;
    final ArrayList<EnumPool<?>> enums;
    final AnyRefType AnyRef;

    /**
     * State Initialization of Fields Array. In C++, it should be possible to memcpy this array into the first field to
     * achieve state initialization.
     * 
     * @note invariant: ∀i. SIFA[i].name == pb.KCN(i)
     */
    public final FieldType<?>[] SIFA;

    /**
     * next SIFA ID to be used if some type is added to SIFA
     */
    protected int nsID;

    /**
     * The next global field ID. Note that this ID does not correspond to the ID used in the file about to be read but
     * to an ID that would be used if it were written.
     * 
     * @note to make this work as intended, merging known fields into the dataFields array has to be done while reading
     *       F.
     * @note ID 0 is reserved for the String hull which is always present
     */
    protected int nextFieldID = 1;

    StateInitializer(FileInputStream in, PoolBuilder pb) {
        this.in = in;

        SIFA = new FieldType[pb.sifaSize];

        Strings = new StringPool(in, pb.literals());

        classes = new ArrayList<>(pb.sifaSize);
        containers = new ArrayList<>();
        enums = new ArrayList<>();

        // TODO sane allocation / implementation of AnyRefType
        AnyRef = new AnyRefType(classes);

        SIFA[0] = BoolType.get();
        SIFA[1] = I8.get();
        SIFA[2] = I16.get();
        SIFA[3] = I32.get();
        SIFA[4] = I64.get();
        SIFA[5] = V64.get();
        SIFA[6] = F32.get();
        SIFA[7] = F64.get();
        SIFA[8] = AnyRef;
        SIFA[9] = Strings;

        nsID = 10;
    }

    /**
     * Calculate correct maxDeps values for containers used by containers.
     */
    protected void fixContainerMD() {
        // increase deps caused by containsers whose maxDeps is nonzero
        for (HullType<?> c : containers) {
            if (c.maxDeps != 0) {
                if (c instanceof SingleArgumentType<?, ?>) {
                    FieldType<?> b = ((SingleArgumentType<?, ?>) c).base;
                    if (b instanceof HullType<?>) {
                        ((HullType<?>) b).maxDeps++;
                    }
                } else {
                    MapType<?, ?> m = (MapType<?, ?>) c;
                    FieldType<?> b = m.keyType;
                    if (b instanceof HullType<?>) {
                        ((HullType<?>) b).maxDeps++;
                    }
                    b = m.valueType;
                    if (b instanceof HullType<?>) {
                        ((HullType<?>) b).maxDeps++;
                    }
                }
            }
        }
    }

    /**
     * Called by the concrete state before returning from the constructor to ensure that potentially running parallel
     * tasks finished.
     */
    public void awaitResults() {
        // nothing by default
    }
}
