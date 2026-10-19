// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.MidiLeveler;

/** MidiLeveler: lifts quiet music, leaves loud music and silence alone, never clips, honours the game's volume. */
public class MidiLevelerTest {
    static int fails;
    static void check(boolean ok, String name) { System.out.println((ok ? "ok    " : "FAIL  ") + name); if (!ok) fails++; }

    /** 440 Hz stereo sine of the given amplitude (0..1), 'seconds' long, as 16-bit LE PCM. */
    static byte[] sine(double amp, double seconds) {
        int frames = (int) (44100 * seconds);
        byte[] b = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            short v = (short) Math.round(Math.sin(2 * Math.PI * 440 * i / 44100.0) * amp * 32767);
            for (int c = 0; c < 2; c++) { b[i * 4 + c * 2] = (byte) v; b[i * 4 + c * 2 + 1] = (byte) (v >> 8); }
        }
        return b;
    }
    static void run(MidiLeveler lv, byte[] b) { for (int o = 0; o + 3528 <= b.length; o += 3528) lv.process(b, o, 3528); }   // 20 ms chunks
    static double rms(byte[] b, int fromFrame, int toFrame) {
        double s = 0; int n = 0;
        for (int f = fromFrame; f < toFrame; f++) { short v = (short) ((b[f * 4] & 0xFF) | (b[f * 4 + 1] << 8)); s += (v / 32768.0) * (v / 32768.0); n++; }
        return Math.sqrt(s / n);
    }
    static double peak(byte[] b) {
        double m = 0;
        for (int i = 0; i + 1 < b.length; i += 2) m = Math.max(m, Math.abs(((short) ((b[i] & 0xFF) | (b[i + 1] << 8))) / 32768.0));
        return m;
    }
    static double db(double x) { return 20 * Math.log10(x); }

    public static void main(String[] a) {
        // quiet music (about -43 dBFS RMS) is lifted to about the target after a short ramp
        byte[] q = sine(0.01, 4);
        MidiLeveler lv = new MidiLeveler(-20, 30, 44100, 2);
        run(lv, q);
        double tail = db(rms(q, 3 * 44100, 4 * 44100));
        check(Math.abs(tail - (-20)) < 1.5, "quiet signal reaches the target (" + String.format("%.1f", tail) + " dBFS)");
        check(lv.levelingDb() > 20 && lv.levelingDb() <= 30.01, "boost stays within the 30 dB limit (" + String.format("%.1f", lv.levelingDb()) + " dB)");

        // the boost limit holds for a very quiet signal
        byte[] vq = sine(0.0012, 4);          // -58 dBFS peak, above the gate
        MidiLeveler lv2 = new MidiLeveler(-20, 30, 44100, 2);
        run(lv2, vq);
        check(lv2.levelingDb() <= 30.01, "very quiet signal is boosted by at most 30 dB");

        // loud music is not attenuated
        byte[] loud = sine(0.5, 2);
        byte[] ref = loud.clone();
        run(new MidiLeveler(-20, 30, 44100, 2), loud);
        check(java.util.Arrays.equals(ref, loud), "loud signal (above target) is left untouched");

        // silence and noise below the gate are not boosted
        byte[] sil = sine(0.0003, 2);         // -70 dBFS
        byte[] silRef = sil.clone();
        run(new MidiLeveler(-20, 30, 44100, 2), sil);
        check(java.util.Arrays.equals(silRef, sil), "signal below -60 dBFS is not boosted");

        // a sudden loud passage after a quiet one never clips and the gain drops at once
        byte[] mix = new byte[(int) (44100 * 3) * 4];
        System.arraycopy(sine(0.01, 2), 0, mix, 0, 44100 * 2 * 4);
        System.arraycopy(sine(0.9, 1), 0, mix, 44100 * 2 * 4, 44100 * 4);
        MidiLeveler lv3 = new MidiLeveler(-20, 30, 44100, 2);
        run(lv3, mix);
        check(peak(mix) <= 0.98, "no clipping when a loud passage follows a boosted quiet one (peak " + String.format("%.3f", peak(mix)) + ")");
        check(db(rms(mix, 2 * 44100 + 4000, 3 * 44100)) > -12, "loud passage keeps its level (" + String.format("%.1f", db(rms(mix, 2 * 44100 + 4000, 3 * 44100))) + " dBFS)");

        // the game's own volume is applied on top
        byte[] v = sine(0.01, 4);
        MidiLeveler lv4 = new MidiLeveler(-20, 30, 44100, 2);
        lv4.setUserGain(0.5f);
        run(lv4, v);
        double half = db(rms(v, 3 * 44100, 4 * 44100));
        check(Math.abs(half - (-26)) < 1.5, "user volume 50% lowers the leveled output by 6 dB (" + String.format("%.1f", half) + " dBFS)");
        byte[] z = sine(0.01, 1);
        MidiLeveler lv5 = new MidiLeveler(-20, 30, 44100, 2);
        lv5.setUserGain(0f);
        run(lv5, z);
        check(peak(z) == 0, "user volume 0 is silent");

        // partial / odd chunk sizes do not break anything
        byte[] odd = sine(0.01, 0.5);
        new MidiLeveler(-20, 30, 44100, 2).process(odd, 0, 1001);   // not a whole number of frames
        check(true, "odd chunk length is handled");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
