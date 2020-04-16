package de.danielscholz.fileIndexer.common;

public class JavaExceptionUtil {

    public static void addSuppressed(Throwable t, Throwable suppressed) {
        t.addSuppressed(suppressed);
    }

}

