// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io;
import java.io.IOException;
public interface SocketConnection extends StreamConnection {
    byte DELAY = 0, LINGER = 1, KEEPALIVE = 2, RCVBUF = 3, SNDBUF = 4;
    void setSocketOption(byte option, int value) throws IllegalArgumentException, IOException;
    int getSocketOption(byte option) throws IllegalArgumentException, IOException;
    String getLocalAddress() throws IOException;
    int getLocalPort() throws IOException;
    String getAddress() throws IOException;
    int getPort() throws IOException;
}
