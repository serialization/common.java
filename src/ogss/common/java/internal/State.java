package ogss.common.java.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ogss.common.java.api.Access;
import ogss.common.java.api.Mode;
import ogss.common.java.api.SkillException;
import ogss.common.java.api.StringAccess;
import ogss.common.java.internal.fieldTypes.AnyRefType;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.FileOutputStream;

/**
 * Implementation common to all skill states independent of type declarations.
 * 
 * @author Timm Felden
 */
public abstract class State implements AutoCloseable {

    /**
     * the guard of the file must not contain \0-characters.
     */
    public String guard;

    // types by skill name
    protected final HashMap<String, FieldType<?>> typeByName;

    public Pool<?> pool(String name) {
        return (Pool<?>) typeByName.get(name);
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

    final StringPool strings;

    /**
     * @return access to known strings
     */
    final public StringAccess Strings() {
        return strings;
    }

    // TODO use counters for classes, enums, containers
    // TODO use Type[] instead
    // TODO add BasePool[] and use it!

    // types in type order
    final protected ArrayList<Pool<?>> classes;
    final protected ArrayList<HullType<?>> containers;

    /**
     * @return iterator over all user types
     */
    final public Iterable<? extends Access<? extends Obj>> allTypes() {
        return classes;
    }

    /**
     * Types required for reflective IO
     */
    protected final AnyRefType annotationType;

    /**
     * Path and mode management can be done for arbitrary states.
     */
    protected State(StateInitializer init) {
        this.strings = init.Strings;
        this.path = init.path;
        this.input = init.in;
        this.canWrite = init.canWrite;
        this.classes = init.classes;
        this.containers = init.containers;
        this.typeByName = init.TBN;
        this.annotationType = init.Annotation;

        for (Pool<?> p : classes)
            p.owner = this;
    }

    /**
     * @return true, iff the argument object is managed by this state
     * @note will return true, if argument is null
     * @note this operation is kind of expensive
     */
    public final boolean contains(Obj target) {
        if (null != target)
            try {
                if (0 < target.ID)
                    return target == ((Pool<?>) typeByName.get(target.typeName())).get(target.ID);
                else if (0 == target.ID)
                    return true; // will evaluate to a null pointer if stored

                return ((Pool<?>) typeByName.get(target.typeName())).newObjects.contains(target);
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
    final public void delete(Obj target) {
        if (null != target && target.ID != 0) {
            ((Pool<?>) typeByName.get(target.typeName())).delete(target);
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

        // ensure that strings are loaded
        strings.loadLazyData();

        // ensure that lazy fields have been loaded
        for (Pool<?> p : classes)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (f instanceof LazyField<?, ?>)
                    ((LazyField<?, ?>) f).ensureLoaded();

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
     * @throws SkillException
     *             if an inconsistency is found
     */
    public void check() throws SkillException {
        // TODO type checks!
        // TODO type restrictions
        // TODO make pools check fields, because they can optimize checks per
        // instance and remove redispatching, if no
        // restrictions apply anyway
        for (Pool<?> p : classes)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                try {
                    f.check();
                } catch (SkillException e) {
                    throw new SkillException(
                            String.format("check failed in %s.%s:\n  %s", p.name, f.name, e.getMessage()), e);
                }
    }

    /**
     * Calculate the closure, like SKilL/Scala.
     * 
     * @todo implement!
     * @throws SkillException
     */
    public void closure() throws SkillException {
        throw new SkillException("TODO");
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
     * @throws SkillException
     *             if check fails
     */
    public void flush() throws SkillException {
        if (!canWrite)
            throw new SkillException("Cannot flush a read only file. Note: close will turn a file into read only.");
        try {
            loadLazyData();
            new Writer(this, new FileOutputStream(path));
            return;
        } catch (SkillException e) {
            throw e;
        } catch (IOException e) {
            throw new SkillException("failed to create or complete out stream", e);
        } catch (Exception e) {
            throw new SkillException("unexpected exception", e);
        }
    }

    /**
     * Same as flush, but will also sync and close file, thus the state must not be used afterwards.
     */
    @Override
    public void close() throws SkillException {
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
