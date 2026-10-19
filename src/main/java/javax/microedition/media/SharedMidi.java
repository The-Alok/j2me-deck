// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import j2medeck.MidiLeveler;
import j2medeck.Settings;

import javax.sound.midi.*;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

/** One shared software synthesizer for all MIDI players (opening one per player is slow and leaky). */
final class SharedMidi {
    private static Synthesizer synth;
    private static boolean tried;
    private static Receiver receiver;
    private static volatile int masterLevel = 100;
    private static long failedAt;
    /** Set while the synthesizer renders through our own output thread (see openLeveled); the game's volume is applied there. */
    private static volatile MidiLeveler leveler;

    private SharedMidi() {}

    static void warmup() {
        Thread t = new Thread(SharedMidi::synth, "midi-warmup");
        t.setDaemon(true);
        t.start();
    }

    static synchronized Synthesizer synth() {
        if (tried && (synth != null || System.currentTimeMillis() - failedAt < 5000)) return synth;
        tried = true;
        try {
            Synthesizer s = MidiSystem.getSynthesizer();
            boolean leveled = Settings.midiBoost && openLeveled(s);
            if (!leveled) s.open();
            File home = new File(System.getProperty("user.home"), ".config/j2me-deck");
            for (String n : new String[]{"soundfont.sf2", "soundfont.dls", "soundfont.sf3"}) {
                File f = new File(home, n);
                if (f.isFile()) {
                    try {
                        Soundbank sb = MidiSystem.getSoundbank(f);
                        if (s.isSoundbankSupported(sb)) {
                            s.unloadAllInstruments(s.getDefaultSoundbank());
                            s.loadAllInstruments(sb);
                            System.err.println("[j2me] soundfont loaded: " + f);
                        }
                    } catch (Exception e) { System.err.println("[j2me] soundfont failed: " + e); }
                    break;
                }
            }
            receiver = s.getReceiver();
            synth = s;
            applyMaster();
            System.err.println("[j2me] MIDI synthesizer ready: " + s.getDeviceInfo().getName() + ", loudness leveling " + (leveled ? "on (target " + Settings.midiTargetDb + " dBFS)" : "off"));
        } catch (Throwable e) {
            failedAt = System.currentTimeMillis();
            System.err.println("[j2me] MIDI synthesizer unavailable (music will be silent): " + e);
        }
        return synth;
    }

    /**
     * Opens Java's software synthesizer so that WE pull its audio and write it to the sound card, which lets MidiLeveler lift
     * quiet music. Needs read access to com.sun.media.sound (run.sh passes --add-exports java.desktop/com.sun.media.sound=
     * ALL-UNNAMED); returns false, with the synthesizer still closed, when that is not possible so the normal open() is used.
     */
    private static boolean openLeveled(Synthesizer s) {
        if (!"com.sun.media.sound.SoftSynthesizer".equals(s.getClass().getName())) return false;
        try {
            AudioFormat fmt = new AudioFormat(44100f, 16, 2, true, false);
            Method open = s.getClass().getMethod("openStream", AudioFormat.class, Map.class);
            AudioInputStream in;
            try { in = (AudioInputStream) open.invoke(s, fmt, null); }
            catch (IllegalAccessException | RuntimeException e) {
                System.err.println("[j2me] MIDI loudness leveling unavailable (start with run.sh, or add --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED): " + e);
                return false;
            }
            SourceDataLine line;
            try {
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, 44100 * 4 / 8);                 // 125 ms of buffer
                line.start();
            } catch (Throwable e) { try { s.close(); } catch (Throwable ignored) {} throw e; }
            MidiLeveler lv = new MidiLeveler(Settings.midiTargetDb, 30, 44100, 2);
            leveler = lv;
            Thread t = new Thread(() -> pump(in, line, lv), "midi-output");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 2);
            t.start();
            return true;
        } catch (Throwable e) {
            leveler = null;
            try { if (s.isOpen()) s.close(); } catch (Throwable ignored) {}
            if (e instanceof RuntimeException) System.err.println("[j2me] MIDI loudness leveling failed, using the plain synthesizer: " + e);
            return false;
        }
    }

    private static void pump(AudioInputStream in, SourceDataLine line, MidiLeveler lv) {
        byte[] buf = new byte[882 * 4];                        // 20 ms
        try {
            while (true) {
                int n = 0;
                while (n < buf.length) { int r = in.read(buf, n, buf.length - n); if (r < 0) return; n += r; }
                lv.process(buf, 0, n);
                line.write(buf, 0, n);
            }
        } catch (Throwable e) {
            if (!(e instanceof java.io.IOException)) System.err.println("[j2me] MIDI output stopped: " + e);
        } finally {
            try { line.stop(); line.close(); } catch (Throwable ignored) {}
        }
    }

    /** Sequencer is never auto-connected: we wire it to the shared synth if there is one. */
    static Sequencer newSequencer() throws MidiUnavailableException {
        Sequencer q = MidiSystem.getSequencer(false);
        q.open();
        Synthesizer s = j2medeck.Settings.mute ? null : synth();
        if (s != null) {
            try { q.getTransmitter().setReceiver(s.getReceiver()); }
            catch (Exception e) { System.err.println("[j2me] cannot connect sequencer: " + e); }
        }
        return q;
    }

    static void setMasterLevel(int level) { masterLevel = level; applyMaster(); }

    private static synchronized void applyMaster() {
        MidiLeveler lv = leveler;
        if (lv != null) { lv.setUserGain(masterLevel / 100f); return; }   // linear, like the sound-effect players' volume
        if (receiver == null) return;
        try {
            int v = Math.max(0, Math.min(16383, masterLevel * 16383 / 100));
            SysexMessage m = new SysexMessage();
            byte[] d = {(byte) 0xF0, 0x7F, 0x7F, 0x04, 0x01, (byte) (v & 0x7F), (byte) (v >> 7), (byte) 0xF7};
            m.setMessage(d, d.length);
            receiver.send(m, -1);
        } catch (Exception ignored) {}
    }

    static void playTone(int note, int ms, int volume) {
        Synthesizer s = synth();
        if (s == null) return;
        MidiChannel ch = s.getChannels()[15];
        if (ch == null) return;
        int vel = Math.max(1, Math.min(127, volume * 127 / 100));
        ch.noteOn(Math.max(0, Math.min(127, note)), vel);
        Thread t = new Thread(() -> {
            try { Thread.sleep(Math.max(1, ms)); } catch (InterruptedException ignored) {}
            ch.noteOff(Math.max(0, Math.min(127, note)));
        }, "tone");
        t.setDaemon(true);
        t.start();
    }
}
