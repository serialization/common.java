package ogss.common.java.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ogss.common.java.api.Access;
import ogss.common.java.api.GeneralAccess;
import ogss.common.java.api.Mode;
import ogss.common.java.api.OGSSException;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.FileOutputStream;

/**
 * Implementation common to all OGSS states independent of type declarations.
 * 
 * @author Timm Felden
 */
public abstract class State implements AutoCloseable {

    static final int HD_Threshold = 16384;
    static final int FD_Threshold = 1048576;

    /**
     * This pool is used for all asynchronous (de)serialization operations.
     */
    static ThreadPoolExecutor pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(), 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("OGSSWorker");
                    return t;
                }
            });

    /**
     * the guard of the file must not contain \0-characters.
     */
    public String guard;

    // types by OGSS name
    private HashMap<String, FieldType<?>> TBN;

    /**
     * @return pool for a given type name
     */
    public Pool<?> pool(String name) {
        if (null == TBN) {
            TBN = new HashMap<>();

            for (Pool<?> p : classes) {
                TBN.put(p.name, p);
            }
        }
        return (Pool<?>) TBN.get(name);
    }

    /**
     * @return the pool corresponding to the dynamic type of the argument Obj
     */
    public Pool<?> pool(Obj ref) {
        if (null == ref) {
            return null;
        } else if (ref instanceof NamedObj)
            return ((NamedObj) ref).τp();
        else
            return (Pool<?>) SIFA[ref.stid()];
    }

    /**
     * True iff the state can perform write operations.
     */
    private boolean canWrite;
    /**
     * path that will be targeted as binary file
     */
    private Path path;
    /**
     * a file input stream keeping the handle to a file for potential write operations
     * 
     * @note this is a consequence of the retarded windows file system
     */
    private FileInputStream input;

    final StringPool strings;

    /**
     * @return access to known strings
     */
    final public GeneralAccess<String> Strings() {
        return strings;
    }

    // field types by statically known ID
    // note to self: try to represent this as 0-size array in C++ to bypass unions or other hacks
    final protected FieldType<?>[] SIFA;

    /**
     * @return the type name of the type of an object.
     */
    public final String typeName(Obj ref) {
        return pool(ref).name;
    }

    // types in type order
    final protected Pool<?>[] classes;
    final protected HullType<?>[] containers;
    final protected EnumPool<?>[] enums;

    /**
     * @return iterator over all user types
     */
    final public Iterable<? extends Access<? extends Obj>> allTypes() {
        return Arrays.asList(classes);
    }

    /**
     * Types required for reflective IO
     */
    protected final AnyRefType anyRefType;

    /**
     * Path and mode management can be done for arbitrary states.
     */
    protected State(StateInitializer init) {
        this.strings = init.Strings;
        this.path = init.path;
        this.input = init.in;
        this.canWrite = init.canWrite;
        this.SIFA = init.SIFA;
        this.classes = init.classes.toArray(new Pool[init.classes.size()]);
        this.containers = init.containers.toArray(new HullType[init.containers.size()]);
        this.enums = init.enums.toArray(new EnumPool[init.enums.size()]);
        this.anyRefType = init.AnyRef;
        anyRefType.owner = this;

        for (Pool<?> p : classes)
            p.owner = this;
    }

    /**
     * @return true, iff the argument object is managed by this state
     * @note will return true, if argument is null
     * @note this operation is kind of expensive
     */
    public final boolean contains(Obj ref) {
        if (null != ref && 0 == ref.ID)
            try {
                Pool<?> p = pool(ref);

                if (0 < ref.ID)
                    return ref == p.data[ref.ID - 1];

                return ref == p.newObjects.get(-1 - ref.ID);
            } catch (Exception e) {
                // out of bounds or similar mean its not one of ours
                return false;
            }
        return true;
    }

    /**
     * ensure that the argument instance will be deleted on next flush
     * 
     * @note safe behaviour for null and duplicate delete
     */
    final public void delete(Obj ref) {
        if (null != ref && ref.ID != 0) {
            pool(ref).delete(ref);
        }
    }

    /**
     * Set a new output path for the file. This will influence the next flush/close operation.
     * 
     * @note The mode will be set to Write.
     * @note (on implementation) memory maps for lazy evaluation must have been created before invocation of this method
     */
    final public void changePath(Path path) {
        this.canWrite = true;
        this.path = path;
    }

    /**
     * @return the current path pointing to the file
     */
    final public Path currentPath() {
        return path;
    }

    /**
     * Set a new mode. The only useful application is to set mode to ReadOnly.
     */
    final public void changeMode(Mode writeMode) {
        // check illegal change
        if (!canWrite)
            throw new IllegalArgumentException("Cannot change from read only, to a write mode.");

        this.canWrite = Mode.Write == writeMode;
        return;
    }

    /**
     * Force all lazy string and field data to be loaded from disk.
     */
    public final void loadLazyData() {
        // check if the file input stream is still open
        if (null == input)
            return;

        // ensure that lazy fields have been loaded
        for (Pool<?> p : classes)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (f instanceof LazyField<?, ?>)
                    ((LazyField<?, ?>) f).ensureLoaded();

        // all strings have been loaded by now
        strings.dropRB();

        // close the file input stream and ensure that it is not read again
        try {
            input.close();
        } catch (IOException e) {
            throw new RuntimeException("internal error", e);
        }
        input = null;
    }

    /**
     * Checks consistency of the current state of the file.
     * 
     * @note it is possible to fix the inconsistency and re-check without breaking the on-disk representation
     * @throws OGSSException
     *             if an inconsistency is found
     */
    public void check() throws OGSSException {
        // TODO type checks!
        // TODO type restrictions
        // TODO make pools check fields, because they can optimize checks per
        // instance and remove redispatching, if no
        // restrictions apply anyway
        for (Pool<?> p : classes)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                try {
                    f.check();
                } catch (OGSSException e) {
                    throw new OGSSException(
                            String.format("check failed in %s.%s:\n  %s", p.name, f.name, e.getMessage()), e);
                }
    }

    /**
     * Calculate a closure.
     * 
     * @todo implement!
     * @throws OGSSException
     */
    public void closure() throws OGSSException {
        throw new OGSSException("TODO");
    }

    /**
     * Drops types and fields from this state that are currently unused.
     * 
     * @note it is an error to modify the state after calling this function
     */
    public void dropUnusedTypes() {
        throw new Error("TODO");
    }

    /**
     * Check consistency and write changes to disk.
     * 
     * @note this will not sync the file to disk, but it will block until all in-memory changes are written to buffers.
     * @throws OGSSException
     *             if check fails
     */
    public void flush() throws OGSSException {
        if (!canWrite)
            throw new OGSSException("Cannot flush a read only file. Note: close will turn a file into read only.");
        try {
            loadLazyData();
            new Writer(this, new FileOutputStream(path));
            return;
        } catch (OGSSException e) {
            throw e;
        } catch (IOException e) {
            throw new OGSSException("failed to create or complete out stream", e);
        } catch (Exception e) {
            throw new OGSSException("unexpected exception", e);
        }
    }

    /**
     * Same as flush, but will also sync and close file, thus the state must not be used afterwards.
     */
    @Override
    public void close() throws OGSSException {
        // flush if required
        if (canWrite) {
            flush();
            this.canWrite = false;
        }

        // close file stream to work around issue with broken Windows FS
        if (null != input) {
            try {
                input.close();
            } catch (IOException e) {
                // we don't care
                e.printStackTrace();
            }
            input = null;
        }
    }
}
