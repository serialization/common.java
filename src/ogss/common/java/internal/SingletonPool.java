package ogss.common.java.internal;

/**
 * Improved API for singleton types.
 * 
 * @note in contrast to modern languages, it is neither possible to provide an
 *       implementation nor to inherit from Pool[T] in Java
 * 
 * @author Timm Felden
 */
public interface SingletonPool<T extends Obj> {

   /**
    * @return the one and only instance
    * 
    * @note this may allocate the instance
    */
   public T get();
}
