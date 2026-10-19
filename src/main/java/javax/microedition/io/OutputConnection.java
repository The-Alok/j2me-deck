// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io;
import java.io.*;
public interface OutputConnection extends Connection {
    OutputStream openOutputStream() throws IOException;
    DataOutputStream openDataOutputStream() throws IOException;
}
