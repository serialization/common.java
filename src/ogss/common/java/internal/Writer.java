package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.SkillException;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileOutputStream;
import ogss.common.streams.OutStream;

final public class Writer {

    protected final State state;

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(Pool<?, ?> p, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(FieldDeclaration<?, ?> f, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    // async reads will post their errors in this queue
    SkillException writeErrors = null;

    // our job synchronisation barrier
    final Semaphore barrier = new Semaphore(0, false);

    // @note can be used to add buffers concurrently to the write queue
    // @note the permit is given after we added a buffer; therefore the reader
    // can always read some buffer if he uses our permit (order is not
    // important)
    final ConcurrentLinkedQueue<BufferedOutStream> finishedBuffers = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<BufferedOutStream> recycleBuffers = new ConcurrentLinkedQueue<>();

    public Writer(State state, FileOutputStream out) throws Exception {
        this.state = state;

        /**
         * *************** * G * ****************
         */
        writeGuard(out);

        /**
         * *************** * S * ****************
         */

        // our string synchronisation barrier
        final Semaphore SB = state.strings.writeBlock(out);

        /**
         * *************** * T F * ****************
         */

        // write T and F to a buffer, while S is written
        final BufferedOutStream buffer = new BufferedOutStream();

        // @note here, the field data write tasks will be started already
        int awaitBuffers = writeTF(buffer);
        SB.acquire();

        // write buffered TF-blocks
        out.write(buffer);
        recycleBuffers.add(buffer);

        /**
         * *************** * HD * ****************
         */

        // await data from all HD tasks
        while (awaitBuffers != 0) {
            awaitBuffers--;
            barrier.acquire();
            final BufferedOutStream buf = finishedBuffers.poll();
            if (null == buf) {
                System.out.println("something went wrong");
            }
            out.writeSized(buf);
            recycleBuffers.add(buf);
        }

        if (0 != barrier.availablePermits()) {
            System.out.println("something went wrong: " + barrier.availablePermits() + " permits remained unused");
        }

        out.close();

        // report errors
        if (null != writeErrors) {
            throw writeErrors;
        }
    }

    private void writeGuard(FileOutputStream out) throws IOException {
        if (null == state.guard || state.guard.isEmpty()) {
            out.i16((short) 0x2622);
        } else {
            out.i8((byte) '#');
            out.put(state.guard.getBytes());
            out.i8((byte) 0);
        }
    }

    /**
     * write T and F, start HD tasks, and return the number of buffers to await
     */
    private int writeTF(BufferedOutStream out) throws Exception {

        // TODO reassign global field IDs!

        // calculate new bpos, sizes, object IDs and compress data arrays
        {
            final int[] newBPOs = new int[state.classes.size()];
            int bases = 0;
            for (Pool<?, ?> p : state.classes) {
                if (p instanceof BasePool<?>) {
                    bases++;
                    State.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            ((BasePool<?>) p).compress(newBPOs);
                            barrier.release();
                        }
                    });
                }
            }
            barrier.acquire(bases);
        }

        /**
         * *************** * T Class * ****************
         */

        // write count of the type block
        out.v64(state.classes.size());

        // write types
        ArrayList<FieldDeclaration<?, ?>> fieldQueue = new ArrayList<>(2 * state.classes.size());
        final HashMap<String, Integer> stringIDs = state.strings.stringIDs;
        for (Pool<?, ?> p : state.classes) {
            out.v64(stringIDs.get(p.name));
            out.v64(p.staticDataInstances);
            restrictions(p, out);
            if (null == p.superPool)
                out.i8((byte) 0);
            else {
                // superID
                out.v64(p.superPool.typeID - 9);
                // our bpo
                out.v64(p.bpo);
            }

            out.v64(p.dataFields.size());

            // add field to queues for description and data tasks
            for (FieldDeclaration<?, ?> f : p.dataFields) {
                fieldQueue.add(f);
                State.pool.execute(new Task(f));
            }
        }

        /**
         * *************** * T Container * ****************
         */

        // write count of the type block
        out.v64(0);
        // TODO container write implementation

        /**
         * *************** * T Enum * ****************
         */

        // write count of the type block
        out.v64(0);
        // TODO enum write implementation

        /**
         * *************** * F * ****************
         */

        for (FieldDeclaration<?, ?> f : fieldQueue) {
            // write info
            out.v64(stringIDs.get(f.name));
            out.v64(f.type.typeID);
            restrictions(f, out);
        }

        // TODO + hull types (+1 for string)
        return fieldQueue.size();
    }

    /**
     * Data structure used for parallel serialization scheduling
     */
    final class Task implements Runnable {
        private final FieldDeclaration<?, ?> f;

        Task(FieldDeclaration<?, ?> f) {
            this.f = f;
        }

        @Override
        public void run() {
            BufferedOutStream buffer = recycleBuffers.poll();
            if (null == buffer) {
                buffer = new BufferedOutStream();
            } else {
                buffer.recycle();
            }

            try {
                Pool<?, ?> owner = f.owner;
                int i = owner.bpo;

                buffer.v64(f.id);
                f.write(i, i + owner.cachedSize, buffer);

            } catch (SkillException e) {
                synchronized (Writer.this) {
                    if (null == writeErrors)
                        writeErrors = e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } catch (Throwable e) {
                synchronized (Writer.this) {
                    e = new SkillException("unexpected failure while writing field " + f.toString(), e);
                    if (null == writeErrors)
                        writeErrors = (SkillException) e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } finally {
                // return the buffer in any case to ensure that there is a
                // buffer on error
                finishedBuffers.add(buffer);

                // ensure that writer can terminate, errors will be
                // printed to command line anyway, and we wont
                // be able to recover, because errors can only happen if
                // the skill implementation itself is
                // broken
                barrier.release();
            }
        }
    }
}
