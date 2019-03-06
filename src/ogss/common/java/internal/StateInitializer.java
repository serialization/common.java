package ogss.common.java.internal;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import ogss.common.java.api.Mode;
import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldTypes.AnyRefType;
import ogss.common.java.internal.fieldTypes.BoolType;
import ogss.common.java.internal.fieldTypes.F32;
import ogss.common.java.internal.fieldTypes.F64;
import ogss.common.java.internal.fieldTypes.I16;
import ogss.common.java.internal.fieldTypes.I32;
import ogss.common.java.internal.fieldTypes.I64;
import ogss.common.java.internal.fieldTypes.I8;
import ogss.common.java.internal.fieldTypes.V64;
import ogss.common.streams.FileInputStream;

/**
 * Initializes a state. One of Creator, Parser, SequentialParser.
 * 
 * @author Timm Felden
 */
public abstract class StateInitializer {

    public static StateInitializer make(Path path, int sifaSize, PoolBuilder pb, KCC[] kccs, Mode... mode) throws IOException {
        final StateInitializer init;
        ActualMode modes = new ActualMode(mode);
        if (modes.create)
            init = new Creator(sifaSize, pb, kccs);
        else {
            FileInputStream fs = FileInputStream.open(path);
            try {
                if (fs.size() < Parser.SEQ_LIMIT)
                    init = new SeqParser(fs, sifaSize, pb, kccs);
                else
                    init = new ParParser(fs, sifaSize, pb, kccs);
            } catch (BufferUnderflowException e) {
                throw new SkillException("unexpected EOF", e);
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
    final HashMap<String, FieldType<?>> TBN = new HashMap<>();
    final AnyRefType Annotation;

    /**
     * State Initialization of Fields Array. In C++, it should be possible to memcpy this array into the first field to
     * achieve state initialization.
     * 
     * @note invariant: ∀i. SIFA[i].getClass == knownClasses[i]
     * @note this is essentially the SKilL/Java large spec passing mode, except that the name binding happens implicitly
     */
    public final Pool<?>[] SIFA;
    final KCC[] kccs;

    /**
     * The next global field ID. Note that this ID does not correspond to the ID used in the file about to be read but
     * to an ID that would be used if it were written.
     * 
     * @note to make this work as intended, merging known fields into the dataFields array has to be done while reading
     *       F.
     * @note ID 0 is reserved for the String hull which is always present
     */
    protected int nextFieldID = 1;

    StateInitializer(FileInputStream in, int sifaSize, KCC[] kccs) {
        this.in = in;

        this.kccs = kccs;
        SIFA = new Pool[sifaSize];

        Strings = new StringPool(in);

        classes = new ArrayList<>(sifaSize);
        containers = new ArrayList<>();

        Annotation = new AnyRefType(classes, TBN);

        TBN.put("bool", BoolType.get());
        TBN.put("i8", I8.get());
        TBN.put("i16", I16.get());
        TBN.put("i32", I32.get());
        TBN.put("i64", I64.get());
        TBN.put("v64", V64.get());
        TBN.put("f32", F32.get());
        TBN.put("f64", F64.get());

        TBN.put(Annotation.name(), Annotation);
        TBN.put(Strings.name(), Strings);
    }

    /**
     * Called by the concrete state before returning from the constructor to ensure that potentially running parallel
     * tasks finished.
     */
    public void awaitResults() {
        // nothing by default
    }
}
