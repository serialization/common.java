package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.java.api.Mode;

/**
 * Transform a list of modes into create/write flags.
 * 
 * @author Timm Felden
 */
public class ActualMode {

    public final boolean create;
    public final boolean write;

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

        this.create = openMode == Mode.Create;
        this.write = closeMode == Mode.Write;
    }
}
