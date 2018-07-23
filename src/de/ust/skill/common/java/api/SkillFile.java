package de.ust.skill.common.java.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import de.ust.skill.common.java.internal.SkillObject;

/**
 * A SKilL file that can be used to access types stored in a skill file and persist changes.
 * 
 * @author Timm Felden
 */
public interface SkillFile extends AutoCloseable {
    /**
     * Modes for file handling.
     * 
     * @author Timm Felden
     */
    public static enum Mode {
        Create, Read, Write, Append, ReadOnly;
    }

    /**
     * Actual mode after processing.
     * 
     * @author Timm Felden
     */
    static class ActualMode {
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
                case Append:
                case Write:
                    if (null == closeMode)
                        closeMode = m;
                    else if (closeMode != m)
                        throw new IOException("You can either write or append to a file.");
                    break;
                case ReadOnly:
                    if (null == closeMode)
                        closeMode = m;
                    else if (closeMode != m)
                        throw new IOException("You cannot combine ReadOnly with another write mode.");
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

    /**
     * @return access to known strings
     */
    public abstract StringAccess Strings();

    /**
     * @return true, iff the argument object is managed by this state
     * @note will return true, if argument is null
     * @note this operation is kind of expensive
     */
    public abstract boolean contains(SkillObject target);

    /**
     * ensure that the argument instance will be deleted on next flush
     */
    public abstract void delete(SkillObject target);

    /**
     * @return iterator over all user types
     */
    public abstract Iterable<? extends Access<? extends SkillObject>> allTypes();

    /**
     * @return stream over all user types
     */
    public abstract Stream<? extends Access<? extends SkillObject>> allTypesStream();

    /**
     * Set a new path for the file. This will influence the next flush/close operation.
     * 
     * @note The mode will be set to Write, if it was ReadOnly before.
     * 
     * @throws IOException
     *             if new path cannot be used for some reason
     * @note (on implementation) memory maps for lazy evaluation must have been created before invocation of this method
     */
    public abstract void changePath(Path path) throws IOException;

    /**
     * @return the current path pointing to the file
     */
    public abstract Path currentPath();

    /**
     * Set a new mode.
     * 
     * @note not fully implemented
     */
    public abstract void changeMode(Mode writeMode);

    /**
     * Force all lazy string and field data to be loaded from disk.
     */
    public abstract void loadLazyData();

    /**
     * Checks consistency of the current state of the file.
     * 
     * @note if check is invoked manually, it is possible to fix the inconsistency and re-check without breaking the
     *       on-disk representation
     * @throws SkillException
     *             if an inconsistency is found
     */
    public abstract void check() throws SkillException;

    /**
     * Check consistency and write changes to disk.
     * 
     * @note this will not sync the file to disk, but it will block until all in-memory changes are written to buffers.
     * @note if check fails, then the state is guaranteed to be unmodified compared to the state before flush
     * @throws SkillException
     *             if check fails
     */
    public abstract void flush() throws SkillException;

    /**
     * Same as flush, but will also sync and close file, thus the state must not be used afterwards.
     */
    public abstract void close() throws SkillException;
}
