// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import javax.microedition.media.control.VolumeControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** MMAPI Player state machine; subclasses implement the do*() hooks. */
public class BasePlayer implements Player, VolumeControl {
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger();
    private final int id = COUNTER.incrementAndGet();
    @Override public String toString() { return getClass().getSimpleName() + "#" + id; }
    protected volatile int state = UNREALIZED;
    protected int loopCount = 1;
    private int level = 100;
    private boolean mute;
    private TimeBase timeBase;
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    public Control getControl(String type) {
        checkRealized();
        if (type == null) throw new IllegalArgumentException();
        if (!type.contains(".")) type = "javax.microedition.media.control." + type;
        return controlFor(type);
    }
    protected Control controlFor(String fullName) { return VolumeControl.class.getName().equals(fullName) ? this : null; }
    public Control[] getControls() { checkRealized(); return new Control[]{this}; }

    public void addPlayerListener(PlayerListener l) { checkClosed(); if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removePlayerListener(PlayerListener l) { checkClosed(); listeners.remove(l); }

    protected void event(String e, Object d) {
        for (PlayerListener l : new ArrayList<>(listeners)) {
            Thread t = new Thread(() -> { try { l.playerUpdate(this, e, d); } catch (Throwable x) { x.printStackTrace(); } }, "MIDletPlayerCallback");
            t.setDaemon(true);
            t.start();
        }
    }

    public synchronized void realize() throws MediaException {
        checkClosed();
        if (state == UNREALIZED) {
            try { doRealize(); } catch (IOException e) { throw new MediaException(String.valueOf(e.getMessage())); }
            state = REALIZED;
        }
    }
    public synchronized void prefetch() throws MediaException {
        checkClosed();
        if (state == UNREALIZED) realize();
        if (state == REALIZED) {
            try { doPrefetch(); } catch (Exception e) { throw new MediaException(String.valueOf(e.getMessage())); }
            state = PREFETCHED;
        }
    }
    public synchronized void start() throws MediaException {
        prefetch();
        if (state == PREFETCHED) { state = STARTED; AudioTrace.log(this, "start"); doStart(); event(PlayerListener.STARTED, getMediaTime()); }
        else AudioTrace.log(this, "start ignored, state=" + state);
    }
    public synchronized void stop() throws MediaException {
        checkClosed();
        if (state == STARTED) { state = PREFETCHED; AudioTrace.log(this, "stop"); doStop(); event(PlayerListener.STOPPED, getMediaTime()); }
    }
    public synchronized void deallocate() {
        if (state == CLOSED) return;
        if (state == STARTED) { state = PREFETCHED; doStop(); }
        if (state == PREFETCHED) { doDeallocate(); state = REALIZED; }
    }
    public synchronized void close() {
        if (state == CLOSED) return;
        deallocate();
        doClose();
        state = CLOSED;
        event(PlayerListener.CLOSED, null);
    }
    public synchronized long setMediaTime(long now) throws MediaException { checkRealized(); doSetMediaTime(now); return getMediaTime(); }
    public long getMediaTime() { checkClosed(); return state < PREFETCHED ? TIME_UNKNOWN : doGetMediaTime(); }
    public long getDuration() { checkClosed(); return state < REALIZED ? TIME_UNKNOWN : doGetDuration(); }
    public void setLoopCount(int c) {
        checkClosed();
        if (state == STARTED) throw new IllegalStateException("player is started");
        if (c == 0) throw new IllegalArgumentException("loop count 0");
        loopCount = c;
        doSetLooping(c);
    }
    public int getState() { return state; }
    public TimeBase getTimeBase() { return timeBase == null ? Manager.getSystemTimeBase() : timeBase; }
    public void setTimeBase(TimeBase t) throws MediaException { timeBase = t; }
    public String getContentType() { checkRealized(); return doGetContentType(); }

    // VolumeControl
    public void setMute(boolean m) { mute = m; doSetVolume(m ? 0 : level); }
    public boolean isMuted() { return mute; }
    public int setLevel(int l) { level = Math.max(0, Math.min(100, l)); if (!mute) doSetVolume(level); event(PlayerListener.VOLUME_CHANGED, this); return level; }
    public int getLevel() { return level; }

    protected void checkClosed() { if (state == CLOSED) throw new IllegalStateException("player is closed"); }
    protected void checkRealized() { checkClosed(); if (state == UNREALIZED) throw new IllegalStateException("call realize() first"); }

    /** Called by subclasses when playback reached the end: player returns to PREFETCHED. */
    protected void endOfMedia() {
        AudioTrace.log(this, "endOfMedia (state was " + state + ")");
        if (state == STARTED) { state = PREFETCHED; }
        event(PlayerListener.END_OF_MEDIA, getDuration());
    }

    protected void doRealize() throws IOException {}
    protected void doPrefetch() throws Exception {}
    protected void doStart() {}
    protected void doStop() {}
    protected void doDeallocate() {}
    protected void doClose() {}
    protected void doSetMediaTime(long u) {}
    protected long doGetMediaTime() { return 0; }
    protected long doGetDuration() { return TIME_UNKNOWN; }
    protected void doSetLooping(int count) {}
    protected String doGetContentType() { return ""; }
    protected void doSetVolume(int level) {}
}
