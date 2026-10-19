// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import javax.microedition.io.Connector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone", MIDI_DEVICE_LOCATOR = "device://midi";
    private static final TimeBase CLOCK = () -> System.nanoTime() / 1000L;

    private Manager() {}

    /** Start the software synthesizer early so the first song doesn't stall the game. */
    public static void warmup() { SharedMidi.warmup(); }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        if (locator == null) throw new IllegalArgumentException("locator");
        if (locator.equals(TONE_DEVICE_LOCATOR)) return new TonePlayer();
        if (locator.equals(MIDI_DEVICE_LOCATOR)) return new BasePlayer();
        if (locator.startsWith("file://")) {
            try (InputStream in = Connector.openInputStream(locator)) { return createPlayer(in, guess(locator)); }
        }
        throw new MediaException("Unsupported locator: " + locator);
    }

    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException {
        if (stream == null) throw new IllegalArgumentException("stream");
        byte[] data = stream.readAllBytes();
        String t = type == null ? sniff(data) : type.toLowerCase();
        InputStream copy = new ByteArrayInputStream(data);
        if (t != null && (t.contains("midi") || t.contains("mid"))) return new MidiPlayer(copy);
        if (t != null && (t.contains("tone"))) return new TonePlayer();
        if (data.length > 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') return new MidiPlayer(copy);
        // WAV, MP3, AMR, AAC...: decoded directly, via ffmpeg if available, or silently as a last resort
        return new DesktopAudioPlayer(copy, type);
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[]{"audio/midi", "audio/x-midi", "audio/wav", "audio/x-wav", "audio/x-tone-seq", "audio/mpeg", "audio/amr"};
    }
    public static String[] getSupportedProtocols(String contentType) { return new String[]{"device", "file"}; }
    public static TimeBase getSystemTimeBase() { return CLOCK; }
    public static void playTone(int note, int duration, int volume) throws MediaException {
        if (note < 0 || note > 127 || duration <= 0) throw new IllegalArgumentException();
        SharedMidi.playTone(note, duration, volume);
    }

    private static String sniff(byte[] d) {
        if (d.length > 4 && d[0] == 'M' && d[1] == 'T' && d[2] == 'h' && d[3] == 'd') return "audio/midi";
        if (d.length > 4 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F') return "audio/x-wav";
        return null;
    }
    private static String guess(String s) {
        int p = s.lastIndexOf('.');
        if (p < 0) return null;
        String e = s.substring(p + 1).toLowerCase();
        return e.equals("wav") ? "audio/wav" : (e.equals("mid") || e.equals("midi")) ? "audio/midi" : null;
    }
}
