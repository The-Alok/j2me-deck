// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io.file;
public interface FileSystemListener {
    int ROOT_ADDED = 0, ROOT_REMOVED = 1;
    void rootChanged(int state, String rootName);
}
