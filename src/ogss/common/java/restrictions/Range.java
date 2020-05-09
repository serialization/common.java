package ogss.common.java.restrictions;

import ogss.common.java.api.OGSSException;
import ogss.common.jvm.streams.InStream;

/**
 * Factory and implementations for all range restrictions
 * 
 * @author Timm Felden
 */
public class Range {
    private Range() {
        // no instance
    }

    /**
     * @return a restriction on success or null on error
     */
    public static FieldRestriction<?> make(int typeID, InStream in) {
        switch (typeID) {
        case 7:
            return new RangeI8(in.i8(), in.i8());
        case 8:
            return new RangeI16(in.i16(), in.i16());
        case 9:
            return new RangeI32(in.i32(), in.i32());
        case 10:
            return new RangeI64(in.i64(), in.i64());
        case 11:
            return new RangeV64(in.v64(), in.v64());
        case 12:
            return new RangeF32(in.f32(), in.f32());
        case 13:
            return new RangeF64(in.f64(), in.f64());
        default:
            return null;
        }
    }

    final static class RangeI8 implements FieldRestriction<Byte> {
        private final byte min;
        private final byte max;

        RangeI8(byte min, byte max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Byte value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeI16 implements FieldRestriction<Short> {
        private final short min;
        private final short max;

        RangeI16(short min, short max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Short value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeI32 implements FieldRestriction<Integer> {
        private final int min;
        private final int max;

        RangeI32(int min, int max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Integer value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeI64 implements FieldRestriction<Long> {
        private final long min;
        private final long max;

        RangeI64(long min, long max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Long value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeV64 implements FieldRestriction<Long> {
        private final long min;
        private final long max;

        RangeV64(long min, long max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Long value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeF32 implements FieldRestriction<Float> {
        private final float min;
        private final float max;

        RangeF32(float min, float max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Float value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }

    final static class RangeF64 implements FieldRestriction<Double> {
        private final double min;
        private final double max;

        RangeF64(double min, double max) {
            this.min = min;
            this.max = max;

        }

        @Override
        public void check(Double value) {
            if (value < min || max < value)
                throw new OGSSException(String.format("%s is not in Range(%d, %d)", value, min, max));
        }
    }
}
