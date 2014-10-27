package org.kframework.utils.file;

public class TTYInfo {

    public final boolean stdin, stdout, stderr;

    public TTYInfo(boolean stdin, boolean stdout, boolean stderr) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
    }
}
