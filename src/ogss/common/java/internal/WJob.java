package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.streams.BufferedOutStream;

/**
 * A Writer Job.
 * 
 * @author Timm Felden
 */
abstract class WJob implements Runnable {
    protected final Writer self;

    WJob(Writer self) {
        this.self = self;
    }

    boolean discard = true;

    WJob tail;

    @Override
    final public void run() {
        BufferedOutStream buffer = self.recycleBuffers.poll();
        if (null == buffer) {
            buffer = new BufferedOutStream();
        } else {
            buffer.recycle();
        }

        try {
            job(buffer);
        } catch (Throwable e) {
            synchronized (self) {
                if (null == self.writeErrors)
                    self.writeErrors = e;
                else
                    self.writeErrors.addSuppressed(e);
            }
        } finally {
            // return the buffer in any case to ensure that there is a
            // buffer on error
            buffer.close();
            if (discard) {
                self.recycleBuffers.add(buffer);
            } else {
                self.finishedBuffers.add(buffer);
            }

            // ensure that writer can terminate, errors will be
            // printed to command line anyway, and we wont
            // be able to recover, because errors can only happen if
            // the OGSS implementation itself is broken
            self.barrier.release();

            if (null != tail)
                tail.run();
        }
    }

    protected abstract void job(BufferedOutStream buffer) throws IOException;
}