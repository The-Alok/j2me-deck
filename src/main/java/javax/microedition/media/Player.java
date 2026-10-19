// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;
public interface Player extends Controllable {
 int CLOSED=0, UNREALIZED=100, REALIZED=200, PREFETCHED=300, STARTED=400; long TIME_UNKNOWN=-1;
 void realize() throws MediaException; void prefetch() throws MediaException; void start() throws MediaException; void stop() throws MediaException; void deallocate(); void close(); long setMediaTime(long now)throws MediaException; long getMediaTime(); TimeBase getTimeBase(); void setTimeBase(TimeBase master)throws MediaException; long getDuration(); void setLoopCount(int count); int getState(); void addPlayerListener(PlayerListener l); void removePlayerListener(PlayerListener l); String getContentType();
}
