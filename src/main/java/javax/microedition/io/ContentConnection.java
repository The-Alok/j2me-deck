// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io;
public interface ContentConnection extends StreamConnection {
    String getType();
    String getEncoding();
    long getLength();
}
