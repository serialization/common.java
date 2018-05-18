package de.ust.skill.common.java.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import de.ust.skill.common.java.api.Access;
import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.api.SkillFile;
import de.ust.skill.common.java.api.StringAccess;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.jvm.streams.FileInputStream;
import de.ust.skill.common.jvm.streams.FileOutputStream;

/**
 * Implementation common to all skill states independent of type declarations.
 * 
 * @author Timm Felden
 */
public abstract class SkillState implements SkillFile {

    /**
     * if we are on windows, then we have to change some implementation details
     */
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

    // types by skill name
    protected final HashMap<String, StoragePool<?, ?>> poolByName;

    public StoragePool<?, ?> pool(String name) {
        return poolByName.get(name);
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
     * a file input stream keeping the handle to a file for potential write
     * operations
     * 
     * @note this is a consequence of the retarded windows file system
     */
    private FileInputStream input;

    /**
     * dirty flag used to prevent append after delete operations
     */
    private boolean dirty = false;

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
                    t.setName("SkillStatePoolThread");
                    return t;
                }
            });

    final StringPool strings;
    /**
     * Types required for reflective IO
     */
    final Annotation annotationType;

    /**
     * Path and mode management can be done for arbitrary states.
     */
    protected SkillState(StringPool strings, Path path, Mode mode, ArrayList<StoragePool<?, ?>> types,
            HashMap<String, StoragePool<?, ?>> poolByName, Annotation annotationType) {
        this.strings = strings;
        this.path = path;
        this.input = strings.getInStream();
        this.writeMode = mode;
        this.types = types;
        this.poolByName = poolByName;
        this.annotationType = annotationType;
    }

    @SuppressWarnings("unchecked")
    protected final void finalizePools(FileInputStream in) {
        try {
            StoragePool.establishNextPools(types);

            // allocate instances
            final Semaphore barrier = new Semaphore(0, false);
            {
                int reads = 0;

                HashSet<String> fieldNames = new HashSet<>();
                for (StoragePool<?, ?> p : (ArrayList<StoragePool<?, ?>>) allTypes()) {

                    // set owners
                    if (p instanceof BasePool<?>) {
                        ((BasePool<?>) p).owner = this;

                        reads += ((BasePool<?>) p).performAllocations(barrier);
                    }

                    // add missing field declarations
                    fieldNames.clear();
                    for (de.ust.skill.common.java.api.FieldDeclaration<?> f : p.dataFields)
                        fieldNames.add(f.name());

                    // ensure existence of known fields
                    for (String n : p.knownFields) {
                        if (!fieldNames.contains(n))
                            p.addKnownField(n, strings, annotationType);
                    }
                }
                barrier.acquire(reads);
            }

            // read field data
            {
                int reads = 0;
                // async reads will post their errors in this queue
                final ArrayList<SkillException> readErrors = new ArrayList<SkillException>();

                for (StoragePool<?, ?> p : (ArrayList<StoragePool<?, ?>>) allTypes()) {
                    // @note this loop must happen in type order!

                    // read known fields
                    for (FieldDeclaration<?, ?> f : p.dataFields)
                        reads += f.finish(barrier, readErrors, in);
                }

                // fix types in the Annotation-runtime type, because we need it
                // in offset calculation
                this.annotationType.fixTypes(poolByName);

                // await async reads
                barrier.acquire(reads);
                for (SkillException e : readErrors) {
                    e.printStackTrace();
                }
                if (!readErrors.isEmpty())
                    throw readErrors.get(0);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    final public StringAccess Strings() {
        return strings;
    }

    @Override
    public final boolean contains(SkillObject target) {
        if (null != target)
            try {
                if (0 < target.skillID)
                    return target == poolByName.get(target.skillName()).getByID(target.skillID);
                else if (0 == target.skillID)
                    return true; // will evaluate to a null pointer if stored

                return poolByName.get(target.skillName()).newObjects.contains(target);
            } catch (Exception e) {
                // out of bounds or similar mean its not one of ours
                return false;
            }
        return true;
    }

    @Override
    final public void delete(SkillObject target) {
        if (null != target) {
            dirty |= target.skillID > 0;
            poolByName.get(target.skillName()).delete(target);
        }
    }

    @Override
    final public void changePath(Path path) throws IOException {
        switch (writeMode) {
        case Write:
            break;
        case Append:
            // catch erroneous behavior
            if (this.path.equals(path))
                return;
            Files.deleteIfExists(path);
            Files.copy(this.path, path);
            break;
        default:
            // dead!
            return;
        }
        this.path = path;

    }

    @Override
    final public Path currentPath() {
        return path;
    }

    @Override
    final public void changeMode(Mode writeMode) {
        // pointless
        if (this.writeMode == writeMode)
            return;

        switch (writeMode) {
        case Write:
            this.writeMode = writeMode;
        case Append:
            // write -> append
            throw new IllegalArgumentException(
                    "Cannot change write mode from Write to Append, try to use open(<path>, Create, Append) instead.");
        case ReadOnly:
            throw new IllegalArgumentException("Cannot change from read only, to a write mode.");

        default:
            // dead, if not used by DAUs
            return;
        }
    }

    @Override
    public final void loadLazyData() {
        // ensure that strings are loaded
        int id = strings.idMap.size();
        while (--id != 0) {
            strings.get(0);
        }

        // ensure that lazy fields have been loaded
        for (StoragePool<?, ?> p : types)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (f instanceof LazyField<?, ?>)
                    ((LazyField<?, ?>) f).ensureLoaded();
    }

    @Override
    public void check() throws SkillException {
        // TODO type restrictions
        // TODO make pools check fields, because they can optimize checks per
        // instance and remove redispatching, if no
        // restrictions apply anyway
        for (StoragePool<?, ?> p : types)
            for (FieldDeclaration<?, ?> f : p.dataFields)
                try {
                    f.check();
                } catch (SkillException e) {
                    throw new SkillException(
                            String.format("check failed in %s.%s:\n  %s", p.name, f.name, e.getMessage()), e);
                }

    }

    @Override
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
                    new StateWriter(this, FileOutputStream.write(makeInStream()));
                    File targetFile = target.toFile();
                    if (targetFile.exists())
                        targetFile.delete();
                    f.renameTo(targetFile);
                    changePath(target);
                } else
                    new StateWriter(this, FileOutputStream.write(makeInStream()));
                return;

            case Append:
                // dirty appends will automatically become writes
                if (dirty) {
                    changeMode(Mode.Write);
                    flush();
                } else
                    new StateAppender(this, FileOutputStream.append(makeInStream()));
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
     * @return the file input stream matching our current status
     */
    private FileInputStream makeInStream() throws IOException {
        if (null == input || !path.equals(input.path()))
            input = FileInputStream.open(path, false);

        return input;
    }

    @Override
    public void close() throws SkillException {
        flush();
        this.writeMode = Mode.ReadOnly;
    }

    // types in type order
    final protected ArrayList<StoragePool<?, ?>> types;

    @Override
    final public Iterable<? extends Access<? extends SkillObject>> allTypes() {
        return types;
    }

    @Override
    final public Stream<? extends Access<? extends SkillObject>> allTypesStream() {
        return types.stream();
    }
}
