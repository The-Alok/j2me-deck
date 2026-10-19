// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

/**
 * Loudness leveler for the software MIDI synthesizer's output (16-bit little-endian PCM).
 *
 * Phone MIDI files are mixed very differently: some tracks are written soft (low velocities and channel volumes) and come out of
 * Java's synthesizer 25 dB or more below a typical sound effect, which makes them practically inaudible. This raises quiet
 * material towards a target RMS level (never above {@code maxBoostDb}, never attenuating) and a per-chunk peak limit
 * guarantees it cannot clip. Silence (below -60 dBFS) is left alone so the synth's noise floor is not pumped up.
 * {@link #setUserGain} is the game's own volume (MMAPI VolumeControl), applied after the leveling.
 */
public final class MidiLeveler {
    private static final double GATE = 0.001;             // -60 dBFS: below this the gain is held
    private static final double PEAK_LIMIT = 0.97;
    private static final double RISE_DB_PER_SEC = 24;
    private static final double RMS_TIME_CONSTANT = 0.3;  // seconds; the loudness estimate rises at once and falls this slowly

    private final double target, maxGain;
    private final int sampleRate, channels;
    private volatile float userGain = 1f;
    private double gain = 1, meanSquare, lastApplied = 1;

    public MidiLeveler(double targetDb, double maxBoostDb, int sampleRate, int channels) {
        this.target = Math.pow(10, targetDb / 20);
        this.maxGain = Math.pow(10, Math.max(0, maxBoostDb) / 20);
        this.sampleRate = sampleRate;
        this.channels = Math.max(1, channels);
    }

    public void setUserGain(float g) { userGain = Math.max(0f, Math.min(4f, g)); }
    /** Current leveling gain in dB (without the user gain); for diagnostics and tests. */
    public double levelingDb() { return 20 * Math.log10(gain); }

    /** Processes {@code len} bytes (whole frames) in place. Call with chunks of about 10-20 ms. */
    public void process(byte[] b, int off, int len) {
        int frameBytes = 2 * channels, frames = len / frameBytes;
        if (frames <= 0) return;
        int samples = frames * channels;
        double sum = 0, peak = 0;
        for (int i = 0, p = off; i < samples; i++, p += 2) {
            double x = ((short) ((b[p] & 0xFF) | (b[p + 1] << 8))) / 32768.0;
            sum += x * x;
            double a = Math.abs(x);
            if (a > peak) peak = a;
        }
        double dt = frames / (double) sampleRate, chunk = sum / samples;
        meanSquare += (1 - Math.exp(-dt / RMS_TIME_CONSTANT)) * (chunk - meanSquare);
        double level = Math.sqrt(Math.max(meanSquare, chunk));
        if (level >= GATE) {
            double desired = Math.max(1, Math.min(maxGain, target / level));
            gain = desired < gain ? desired : Math.min(desired, gain * Math.pow(10, RISE_DB_PER_SEC * dt / 20));
        }
        if (peak > 0 && gain * peak > PEAK_LIMIT) gain = PEAK_LIMIT / peak;
        double applied = gain * userGain, from = applied < lastApplied ? applied : lastApplied;   // never ramp down, it could clip
        lastApplied = applied;
        for (int f = 0, p = off; f < frames; f++) {
            double g = from + (applied - from) * (f + 1) / frames;
            for (int c = 0; c < channels; c++, p += 2) {
                double v = ((short) ((b[p] & 0xFF) | (b[p + 1] << 8))) * g;
                int s = (int) Math.round(Math.max(-32768, Math.min(32767, v)));
                b[p] = (byte) s;
                b[p + 1] = (byte) (s >> 8);
            }
        }
    }
}
