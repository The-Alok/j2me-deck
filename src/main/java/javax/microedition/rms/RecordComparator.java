// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.rms;
public interface RecordComparator {
    int EQUIVALENT = 0, FOLLOWS = 1, PRECEDES = -1;
    int compare(byte[] rec1, byte[] rec2);
}
