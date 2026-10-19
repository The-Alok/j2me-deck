// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.sound;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import java.io.ByteArrayInputStream;

/** Nokia Sound API. WAV data plays through MMAPI; Nokia ring-tone (FORMAT_TONE) data is accepted but silent. */
public class Sound {
    public static final int FORMAT_TONE = 1, FORMAT_WAV = 5;
    public static final int SOUND_PLAYING = 0, SOUND_STOPPED = 1, SOUND_UNINITIALIZED = 3;

    private Player player;
    private int state = SOUND_UNINITIALIZED, gain = 255;
    private SoundListener listener;

    public Sound(byte[] data, int type) { init(data, type); }
    public Sound(int freq, long duration) { init(freq, duration); }

    public static int getConcurrentSoundCount(int type) { return 1; }
    public static int[] getSupportedFormats() { return new int[]{FORMAT_TONE, FORMAT_WAV}; }

    public void init(byte[] data, int type) {
        release();
        if (type == FORMAT_WAV) {
            try {
                player = Manager.createPlayer(new ByteArrayInputStream(data), "audio/wav");
                player.realize();
                player.addPlayerListener((p, ev, d) -> { if ("endOfMedia".equals(ev)) { state = SOUND_STOPPED; notifyState(); } });
            } catch (Exception e) { player = null; }
        }
        state = SOUND_STOPPED;
    }
    public void init(int freq, long duration) { release(); state = SOUND_STOPPED; }
    public int getGain() { return gain; }
    public void setGain(int g) { gain = Math.max(0, Math.min(255, g)); }
    public int getState() { return state; }
    public void play(int loop) {
        if (state == SOUND_UNINITIALIZED) return;
        state = SOUND_PLAYING;
        notifyState();
        try {
            if (player != null) { player.setLoopCount(loop == 0 ? -1 : loop); player.start(); return; }
        } catch (Exception ignored) {}
        state = SOUND_STOPPED;
        notifyState();
    }
    public void stop() { try { if (player != null) player.stop(); } catch (Exception ignored) {} if (state != SOUND_UNINITIALIZED) state = SOUND_STOPPED; }
    public void resume() { play(1); }
    public void release() { try { if (player != null) player.close(); } catch (Exception ignored) {} player = null; state = SOUND_UNINITIALIZED; }
    public void setSoundListener(SoundListener l) { listener = l; }
    private void notifyState() {
        SoundListener l = listener;
        if (l != null) { final int s = state; new Thread(() -> l.soundStateChanged(this, s), "nokia-sound").start(); }
    }
}
