package ogss.common.java.internal;

import java.io.File;
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

    /**
     * if we are on windows, then we have to change some implementation details
     */
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

    // types by skill name
    protected final HashMap<String, FieldType<?>> typeByName;

    public Pool<?> pool(String name) {
        return (Pool<?>) typeByName.get(name);
    }

    /**
     * write mode this state is operating on
     */
    private Mode writeMode;
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
    protected State(StateInitializer initial, Mode mode) {
        this.strings = initial.Strings;
        this.path = initial.in.path();
        this.input = initial.in;
        this.writeMode = mode;
        this.classes = initial.classes;
        this.containers = initial.containers;
        this.typeByName = initial.typeByName;
        this.annotationType = initial.Annotation;

        for (Pool<?> p : classes)
            p.owner = this;

        // finalizePools();
    }

    // @SuppressWarnings("unchecked")
    // private final void finalizePools() {
    //
    // // TODO this should be done differently
    // if (strings.knownStrings.isEmpty()) {
    // // add all type and field names, if there are no known strings, as they cannot have been read from file
    // for (Pool<?, ?> p : classes) {
    // strings.add(p.name);
    // for (FieldDeclaration<?, ?> f : p.dataFields) {
    // strings.add(f.name);
    // }
    // }
    // }
    //
    // try {
    //
    // // allocate instances
    // final Semaphore barrier = new Semaphore(0, false);
    // {
    // int reads = 0;
    //
    // HashSet<String> fieldNames = new HashSet<>();
    // for (Pool<?, ?> p : (ArrayList<Pool<?, ?>>) allTypes()) {
    //
    // // set owners
    // if (p instanceof BasePool<?>) {
    // ((BasePool<?>) p).owner = this;
    //
    // reads += ((BasePool<?>) p).performAllocations(barrier);
    // }
    //
    // // add missing field declarations
    // fieldNames.clear();
    // for (ogss.common.java.api.FieldDeclaration<?> f : p.dataFields)
    // fieldNames.add(f.name());
    //
    // // ensure existence of known fields
    // for (String n : p.knownFields) {
    // if (!fieldNames.contains(n))
    // p.addKnownField(n, strings, annotationType);
    // }
    // }
    // barrier.acquire(reads);
    // }
    //
    // // read field data
    // {
    // int reads = 0;
    // // async reads will post their errors in this queue
    // final ArrayList<SkillException> readErrors = new ArrayList<SkillException>();
    //
    // for (Pool<?, ?> p : (ArrayList<Pool<?, ?>>) allTypes()) {
    // // @note this loop must happen in type order!
    //
    // // read known fields
    //// for (FieldDeclaration<?, ?> f : p.dataFields)
    //// reads += f.finish(barrier, readErrors, input);
    // }
    //
    // // fix types in the Annotation-runtime type, because we need it
    // // in offset calculation
    // this.annotationType.fixTypes(poolByName);
    //
    // // await async reads
    // barrier.acquire(reads);
    // for (SkillException e : readErrors) {
    // e.printStackTrace();
    // }
    // if (!readErrors.isEmpty())
    // throw readErrors.get(0);
    // }
    // } catch (InterruptedException e) {
    // e.printStackTrace();
    // }
    // }

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
     * @note The mode will be set to Write, if it was ReadOnly before.
     * @note (on implementation) memory maps for lazy evaluation must have been created before invocation of this method
     */
    final public void changePath(Path path) {
        this.writeMode = Mode.Write;
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
        if (this.writeMode != writeMode && this.writeMode == Mode.ReadOnly)
            throw new IllegalArgumentException("Cannot change from read only, to a write mode.");

        this.writeMode = writeMode;
        return;
    }

    /**
     * Force all lazy string and field data to be loaded from disk.
     */
    public final void loadLazyData() {
        // ensure that strings are loaded
        int id = strings.idMap.size();
        while (--id != 0) {
            strings.get(0);
        }

        // ensure that lazy fields have been loaded
        for (Pool<?> p : classes)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (f instanceof LazyField<?, ?>)
                    ((LazyField<?, ?>) f).ensureLoaded();
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
        try {
            switch (writeMode) {
            case Write:
                if (isWindows) {
                    // we have to write into a temporary file and move the file
                    // afterwards
                    Path target = path;
                    File f = File.createTempFile("write", ".sf");
                    f.createNewFile();
                    f.deleteOnExit();
                    changePath(f.toPath());
                    new Writer(this, new FileOutputStream(makeInStream()));
                    File targetFile = target.toFile();
                    if (targetFile.exists())
                        targetFile.delete();
                    f.renameTo(targetFile);
                    changePath(target);
                } else
                    new Writer(this, new FileOutputStream(makeInStream()));
                return;

            case ReadOnly:
                throw new SkillException("Cannot flush a read only file. Note: close will turn a file into read only.");

            default:
                // dead
                break;
            }
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
        if (Mode.ReadOnly != writeMode) {
            flush();
            this.writeMode = Mode.ReadOnly;
        }

        // close file stream to work around issue with broken Windows FS
        if (null != input)
            try {
                input.close();
            } catch (IOException e) {
                // we don't care
                e.printStackTrace();
            }
    }

    /**
     * @return the file input stream matching our current status
     */
    private FileInputStream makeInStream() throws IOException {
        if (null == input || !path.equals(input.path()))
            input = FileInputStream.open(path, false);

        return input;
    }

    /**
     * Actual mode after processing.
     * 
     * @author Timm Felden
     */
    protected static final class ActualMode {
        public final Mode open;
        public final Mode close;

        public ActualMode(Mode... modes) throws IOException {
            // determine open mode
            // @note read is preferred over create, because empty files are
            // legal and the file has been created by now if it did not exist
            // yet
            // @note write is preferred over append, because usage is more
            // inuitive
            Mode openMode = null, closeMode = null;
            for (Mode m : modes)
                switch (m) {
                case Create:
                case Read:
                    if (null == openMode)
                        openMode = m;
                    else if (openMode != m)
                        throw new IOException("You can either create or read a file.");
                    break;
                case ReadOnly:
                case Write:
                    if (null == closeMode)
                        closeMode = m;
                    else if (closeMode != m)
                        throw new IOException("You can use either write or readOnly.");
                    break;
                default:
                    break;
                }
            if (null == openMode)
                openMode = Mode.Read;
            if (null == closeMode)
                closeMode = Mode.Write;

            this.open = openMode;
            this.close = closeMode;
        }
    }
}
