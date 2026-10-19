// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import javax.microedition.media.control.ToneControl;
import javax.sound.midi.*;
import java.util.HashMap;
import java.util.Map;

/** Player for MMAPI tone sequences: the byte sequence is translated into a MIDI sequence and played like any MIDI file. */
final class TonePlayer extends MidiPlayer implements ToneControl {
    TonePlayer() { super(new byte[0]); }

    @Override protected void doRealize() { /* sequence arrives through setSequence() */ }

    @Override protected Control controlFor(String type) {
        if ((ToneControl.class.getName()).equals(type)) return this;
        return super.controlFor(type);
    }

    @Override public void setSequence(byte[] seq) {
        if (seq == null) throw new IllegalArgumentException("sequence");
        if (state == STARTED) throw new IllegalStateException("player is started");
        try { setSequence(build(seq)); } catch (InvalidMidiDataException e) { throw new IllegalArgumentException("bad tone sequence: " + e.getMessage()); }
    }

    /** Tone sequence format: version, tempo, resolution, blocks, repeats, volume, then (note, duration) pairs. */
    static Sequence build(byte[] s) throws InvalidMidiDataException {
        int tempo = 30, resolution = 64, volume = 100, i = 0;
        if (s.length < 4 || s[0] != VERSION || s[1] != 1) throw new IllegalArgumentException("missing version header");
        i = 2;
        Map<Integer, int[]> blocks = new HashMap<>();   // block -> [start, end) in s
        // first pass: header + block table (blocks may be defined before use only, per spec)
        Sequence seq = null; Track track = null; long tick = 0;
        // Resolve everything with a small interpreter over the byte array
        java.util.List<int[]> notes = new java.util.ArrayList<>();   // {tick, note, dur, volume}
        tick = interpret(s, 2, s.length, blocks, notes, new int[]{tempo, resolution, volume}, 0);
        int[] hdr = header(s);
        tempo = hdr[0]; resolution = hdr[1];
        seq = new Sequence(Sequence.PPQ, Math.max(1, resolution / 4));
        track = seq.createTrack();
        int mpq = 60_000_000 / Math.max(1, tempo * 4);
        MetaMessage mm = new MetaMessage();
        mm.setMessage(0x51, new byte[]{(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3);
        track.add(new MidiEvent(mm, 0));
        for (int[] n : notes) {
            if (n[1] < 0) continue;     // silence
            int vel = Math.max(1, Math.min(127, n[3] * 127 / 100));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, n[1], vel), n[0]));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, n[1], 0), n[0] + Math.max(1, n[2])));
        }
        // end marker so the sequencer reports end-of-track right after the last note
        MetaMessage end = new MetaMessage();
        end.setMessage(0x2F, new byte[0], 0);
        track.add(new MidiEvent(end, Math.max(tick, track.ticks())));
        return seq;
    }

    private static int[] header(byte[] s) {
        int tempo = 30, res = 64;
        for (int i = 2; i + 1 < s.length; ) {
            byte b = s[i];
            if (b == TEMPO) { tempo = s[i + 1] & 0xFF; i += 2; }
            else if (b == RESOLUTION) { res = s[i + 1] & 0xFF; i += 2; }
            else break;
        }
        return new int[]{tempo, res};
    }

    private static long interpret(byte[] s, int from, int to, Map<Integer, int[]> blocks, java.util.List<int[]> out, int[] st, long tick) {
        int vol = st[2];
        int i = from;
        while (i < to) {
            byte b = s[i];
            switch (b) {
                case TEMPO: case RESOLUTION: i += 2; break;
                case SET_VOLUME: vol = s[i + 1]; i += 2; break;
                case BLOCK_START: {
                    int id = s[i + 1], j = i + 2;
                    while (j < to && !(s[j] == BLOCK_END && s[j + 1] == id)) j += (s[j] < 0 ? 2 : 2);
                    blocks.put(id, new int[]{i + 2, j});
                    i = j + 2;
                    break;
                }
                case PLAY_BLOCK: { int[] r = blocks.get((int) s[i + 1]); if (r != null) tick = interpret(s, r[0], r[1], blocks, out, new int[]{st[0], st[1], vol}, tick); i += 2; break; }
                case REPEAT: {
                    int times = s[i + 1], tone = s[i + 2], dur = s[i + 3];
                    // REPEAT multiplier, tone, duration: play that note `times` times
                    for (int k = 0; k < times; k++) { out.add(new int[]{(int) tick, tone, dur, vol}); tick += dur; }
                    i += 4;
                    break;
                }
                case VERSION: i += 2; break;
                default: {
                    if (b < 0 && b != SILENCE) { i += 2; break; }
                    int dur = s[i + 1];
                    out.add(new int[]{(int) tick, b, dur, vol});
                    tick += dur;
                    i += 2;
                }
            }
        }
        return tick;
    }
}
