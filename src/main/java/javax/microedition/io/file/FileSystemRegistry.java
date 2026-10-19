// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io.file;

import java.io.File;
import java.util.*;

public final class FileSystemRegistry {
    private FileSystemRegistry() {}
    public static Enumeration listRoots() {
        Vector<String> v = new Vector<>();
        File[] roots = FileConnectionImpl.sandbox().listFiles(File::isDirectory);
        if (roots != null) for (File r : roots) v.add(r.getName() + "/");
        if (!v.contains("root1/")) v.add(0, "root1/");
        return v.elements();
    }
    public static boolean addFileSystemListener(FileSystemListener l) { return false; }
    public static boolean removeFileSystemListener(FileSystemListener l) { return false; }
}
