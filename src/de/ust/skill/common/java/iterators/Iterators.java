package de.ust.skill.common.java.iterators;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Advanced iterator handling.
 * 
 * @author Timm Felden
 */
public final class Iterators {
    private Iterators() {
        // there is no instance
    }

    public static <T> Iterator<T> array(T[] target) {
        if (null == target || 0 == target.length)
            return EmptyIterator.<T>get();
        return new ArrayIterator<T>(target);
    }

    /**
     * iterates over a view of an array
     * 
     * @param target
     *            viewed array
     * @param begin
     *            first index to be used
     * @param end
     *            first index _not_ to be used
     * @return iterator over the supplied range
     */
    public static <T> Iterator<T> array(T[] target, int begin, int end) {
        if (null == target || 0 == target.length || end <= begin)
            return EmptyIterator.<T>get();
        return new ArrayViewIterator<T>(target, begin, end);
    }

    /**
     * iterates over a view of an array
     * 
     * @param target
     *            viewed array
     * @param begin
     *            first index to be used
     * @param end
     *            first index _not_ to be used
     * @return iterator over the supplied range
     */
    public static <T extends B, B> Iterator<T> fakeArray(B[] target, int begin, int end) {
        if (null == target || 0 == target.length || end <= begin)
            return EmptyIterator.<T>get();
        return new FakeArrayViewIterator<T, B>(target, begin, end);
    }

    public static <T> Iterator<T> empyt() {
        return EmptyIterator.<T>get();
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static <T> Iterator<T> concatenate(Iterator<? extends T>... is) {
        if (0 == is.length)
            return EmptyIterator.<T>get();
        if (1 == is.length)
            return (Iterator<T>) is[0];

        // filter empty iterators
        LinkedList<Iterator<? extends T>> iterators = new LinkedList<>();
        for (Iterator<? extends T> i : is)
            if (i.hasNext())
                iterators.addLast(i);
        if (iterators.isEmpty())
            return EmptyIterator.<T>get();
        else if (iterators.size() == 1)
            return (Iterator<T>) iterators.getFirst();

        return new CombinedIterator<T>(iterators);
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> concatenate(Collection<Iterator<? extends T>> is) {
        final int length = is.size();
        if (0 == length)
            return EmptyIterator.<T>get();

        // filter empty iterators
        LinkedList<Iterator<? extends T>> iterators = new LinkedList<>();
        for (Iterator<? extends T> i : is)
            if (i.hasNext())
                iterators.addLast(i);
        if (iterators.isEmpty())
            return EmptyIterator.<T>get();
        else if (iterators.size() == 1)
            return (Iterator<T>) iterators.getFirst();

        return new CombinedIterator<T>(iterators);
    }
}
