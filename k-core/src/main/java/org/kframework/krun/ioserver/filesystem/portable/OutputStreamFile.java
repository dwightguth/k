// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.krun.ioserver.filesystem.portable;

import java.io.IOException;
import java.io.OutputStream;

import org.kframework.utils.errorsystem.KExceptionManager;

public class OutputStreamFile extends File {

    protected OutputStream os;

    private final KExceptionManager kem;

    public OutputStreamFile(OutputStream os, KExceptionManager kem) {
        this.os = os;
        this.kem = kem;
    }

    public long tell() throws IOException {
        kem.registerInternalWarning("Potentially unsound file system behavior: attempting to tell from "
                + "stdout or stderr. If these were redirected to a file, you may incorrectly get an #ESPIPE "
                + "instead of the correct result. If you are interested in the correct behavior, please "
                + "file an issue on github.");
        throw new IOException("ESPIPE");
        // technically the above code is incorrect (it should be what is below); however,
        // we cannot guarantee in a client/server architecture that we have access to
        // the original file descriptors of the client, so we can't actually support this
        // behavior. Since it is a quite minor use case, I elected to log a warning
        // informing the user of the incorrect behavior rather than trying to fix it,
        // a solution that would likely require changes to third-party C code.
//        try {
//            return os.getChannel().position();
//        } catch (IOException e) {
//            PortableFileSystem.processIOException(e);
//            throw e; //unreachable
//        }
    }

    public void seek(long pos) throws IOException {
        kem.registerInternalWarning("Potentially unsound file system behavior: attempting to seek from "
                + "stdout or stderr. If these were redirected to a file, you may incorrectly get an #ESPIPE "
                + "instead of the correct result. If you are interested in the correct behavior, please "
                + "file an issue on github.");
        throw new IOException("ESPIPE");
        //see comment on tell
//        try {
//            os.getChannel().position(pos);
//        } catch (IOException e) {
//            PortableFileSystem.processIOException(e);
//            throw e; //unreachable
//        }
    }

    public void putc(byte b) throws IOException {
        try {
            os.write(b);
        } catch (IOException e) {
            PortableFileSystem.processIOException(e);
        }
    }

    public byte getc() throws IOException {
        //since technically reading from file descriptors oen and two can succeed or fail depending
        //on what underlying file they point to and what mode they were opened in, we don't throw
        //an IOException here. An IOException in the api corresponds to a system call failing in a
        //well-defined fashion. Here we throw an UnsupportedOperationException because the behavior
        //is undefined.
        throw new UnsupportedOperationException();
    }

    public byte[] read(int n) throws IOException {
        //see comment on getc
        throw new UnsupportedOperationException();
    }

    public void write(byte[] b) throws IOException {
        try {
            os.write(b);
        } catch (IOException e) {
            PortableFileSystem.processIOException(e);
        }
    }

    void close() throws IOException {
        try {
            os.close();
        } catch (IOException e) {
            PortableFileSystem.processIOException(e);
        }
    }
}
