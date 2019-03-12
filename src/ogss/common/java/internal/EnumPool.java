package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * This pool holds all instances of an enum from the perspective of a file.
 * 
 * @note neither the inherited FieldType nor EnumProxy can be typed correctly. In case of FieldType, there are some
 *       insanities in the generic Java overriding rules preventing our code to be accepted if typed.
 * @author Timm Felden
 */
public final class EnumPool<T extends Enum<T>> extends FieldType {

    final String name;

    /**
     * values as seen from the combined specification
     */
    final EnumProxy<T>[] values;

    /**
     * values from the perspective of the files specification, i.e. this table is used to decode values from disc
     */
    final EnumProxy<T>[] fileValues;

    /**
     * values from the perspective of the tools specification, i.e. this table is used to convert enum values to proxies
     */
    private final EnumProxy<T>[] staticValues;

    /**
     * If first is null, an unknown pool is constructed
     */
    EnumPool(int tid, String name, String[] values, T[] known) {
        super(tid);
        this.name = name;

        if (null == values) {
            // only known values, none from file
            // @note we set file values anyway to get sane default values
            this.fileValues = this.values = this.staticValues = new EnumProxy[known.length];
            for (int ki = 0; ki < known.length; ki++) {
                staticValues[ki] = new EnumProxy<T>(known[ki], this, known[ki].toString(), ki);
            }
        } else {
            this.fileValues = new EnumProxy[values.length];

            // check if there is a known enum associated with this pool
            if (null == known) {
                this.values = fileValues;
                staticValues = null;
                for (int i = 0; i < values.length; i++) {
                    this.values[i] = new EnumProxy<T>(null, this, values[i], i);
                }
            } else {
                this.staticValues = new EnumProxy[known.length];

                // merge file values and statically known values
                ArrayList<EnumProxy<T>> vs = new ArrayList<>();

                int id = 0, vi = 0, ki = 0;
                EnumProxy<T> p;
                while (vi < values.length | ki < known.length) {
                    int cmp = ki < known.length
                            ? (vi < values.length ? Parser.compare(values[vi], known[ki].toString()) : 1)
                            : -1;

                    if (0 == cmp) {
                        p = new EnumProxy<T>(known[ki], this, values[vi], id++);
                        fileValues[vi++] = p;
                        staticValues[ki++] = p;
                        vs.add(p);

                    } else if (cmp < 0) {
                        p = new EnumProxy<T>(null, this, values[vi], id++);
                        fileValues[vi++] = p;
                        vs.add(p);
                    } else {
                        p = new EnumProxy<T>(known[ki], this, known[ki].toString(), id++);
                        staticValues[ki++] = p;
                        vs.add(p);
                    }
                }
                // create values
                if (staticValues.length == fileValues.length) {
                    this.values = fileValues;
                } else {
                    // there are unknown values
                    this.values = vs.toArray(new EnumProxy[vs.size()]);
                }
            }
        }
    }

    public EnumProxy<T> proxy(T target) {
        return staticValues[target.ordinal()];
    }

    @Override
    public EnumProxy<T> r(InStream in) {
        return fileValues[in.v32()];
    }

    /**
     * We want to allow writing of both EnumProxy<T> and T, but Java would not allow us to do that, if we would tell the
     * compiler, that this is a FieldType<EnumProxy<T>>.
     */
    @Override
    public boolean w(Object data, OutStream out) throws IOException {
        if (null == data) {
            out.i8((byte) 0);
            return true;
        }
        final int id;
        if (data instanceof Enum<?>) {
            id = staticValues[((Enum<?>) data).ordinal()].id;
        } else {
            id = ((EnumProxy<?>) data).id;
        }
        out.v64(id);
        return 0 == id;
    }

    @Override
    public String toString() {
        return name;
    }

    enum Test {
        eins, zwei, drei;
    }
}
