// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import javax.sound.midi.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/** Standard MIDI file player. Always reports END_OF_MEDIA, even when no audio device is present. */
class MidiPlayer extends BasePlayer {
    private final byte[] data;
    protected Sequence sequence;
    private Sequencer seq;
    private int endCount;
    private long pausedAt;

    MidiPlayer(InputStream in) throws IOException { data = in.readAllBytes(); }
    MidiPlayer(byte[] d) { data = d; }
    final void setSequence(Sequence s) { sequence = s; }

    @Override protected void doRealize() throws IOException {
        try { sequence = MidiSystem.getSequence(new ByteArrayInputStream(data)); }
        catch (InvalidMidiDataException e) { throw new IOException("Invalid MIDI data: " + e.getMessage()); }
    }

    @Override protected void doPrefetch() throws Exception {
        if (seq == null) {
            seq = SharedMidi.newSequencer();
            seq.setSequence(sequence);
            seq.addMetaEventListener(m -> { if (m.getType() == 47) trackEnded(); });
            seq.setMicrosecondPosition(pausedAt);
        }
    }

    private void trackEnded() {
        int loops = loopCount;
        if (loops == -1) return;
        if (loops > 1 && ++endCount < loops) return;
        endCount = 0;
        pausedAt = 0;
        if (state == STARTED) endOfMedia();
    }

    @Override protected void doStart() {
        if (seq == null) return;
        endCount = 0;
        applyLoop(loopCount);
        seq.start();
    }
    @Override protected void doStop() { if (seq != null) { pausedAt = seq.getMicrosecondPosition(); seq.stop(); } }
    @Override protected void doDeallocate() { releaseSequencer(); }
    @Override protected void doClose() { releaseSequencer(); }
    private void releaseSequencer() {
        Sequencer s = seq; seq = null;
        if (s != null) { try { s.stop(); s.close(); } catch (Exception ignored) {} }
    }
    private void applyLoop(int count) {
        if (seq == null) return;
        // Gervill loops at the sequencer level; END_OF_MEDIA is generated when the last loop finishes.
        seq.setLoopCount(count == -1 ? Sequencer.LOOP_CONTINUOUSLY : 0);
    }
    @Override protected void doSetLooping(int count) {}
    @Override protected void doSetMediaTime(long us) { pausedAt = us; if (seq != null) seq.setMicrosecondPosition(us); }
    @Override protected long doGetMediaTime() { return seq == null ? pausedAt : seq.getMicrosecondPosition(); }
    @Override protected long doGetDuration() { return sequence == null ? TIME_UNKNOWN : sequence.getMicrosecondLength(); }
    @Override protected String doGetContentType() { return "audio/midi"; }
    @Override protected void doSetVolume(int level) { SharedMidi.setMasterLevel(level); }
}
