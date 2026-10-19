// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PCM/WAV/AU/AIFF clip player via javax.sound.sampled. Other formats are converted with ffmpeg when present;
 * if that is impossible the player stays silent but still reports end-of-media so game logic keeps working.
 */
final class DesktopAudioPlayer extends BasePlayer {
    private byte[] data;
    private final String type;
    private Clip clip;
    private boolean stoppedByUser, silent;
    private Timer timer;
    private int playGen;

    DesktopAudioPlayer(InputStream in, String type) throws IOException { this.data = in.readAllBytes(); this.type = type; }

    private Clip open(byte[] bytes) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
        Clip c = AudioSystem.getClip();
        c.open(ais);
        c.addLineListener(ev -> {
            if (ev.getType() == LineEvent.Type.STOP) AudioTrace.log(this, "clip STOP event, position " + c.getFramePosition() + "/" + c.getFrameLength() + (stoppedByUser ? " (stopped by game)" : ""));
            if (ev.getType() == LineEvent.Type.STOP && !stoppedByUser && c.getFramePosition() >= c.getFrameLength() - 1) {
                if (state == STARTED) endOfMedia();
            }
        });
        return c;
    }

    @Override protected void doRealize() throws IOException {
        if (j2medeck.Settings.mute) { silent = true; return; }
        try { clip = open(data); AudioTrace.log(this, "realized as clip, " + clip.getMicrosecondLength() / 1000 + " ms"); return; } catch (Throwable first) {
            byte[] wav = FfmpegDecoder.toWav(data);
            if (wav != null) { try { clip = open(wav); AudioTrace.log(this, "realized via ffmpeg, " + clip.getMicrosecondLength() / 1000 + " ms"); return; } catch (Throwable ignored) {} }
        }
        silent = true;
        System.err.println("[j2me] audio format not supported (" + type + "); install ffmpeg to enable it. Playing silently.");
    }
    @Override protected void doStart() {
        stoppedByUser = false;
        if (clip == null) {
            timer = new Timer(true);
            timer.schedule(new TimerTask() { public void run() { endOfMedia(); } }, 500);
            return;
        }
        if (clip.getFramePosition() >= clip.getFrameLength() - 1) clip.setFramePosition(0);
        if (loopCount == -1) clip.loop(Clip.LOOP_CONTINUOUSLY);
        else if (loopCount > 1) clip.loop(loopCount - 1);
        else clip.start();
        if (loopCount == 1) watchdog();
    }

    /** Reports end-of-media from a timer too, so a missing or odd line STOP event (position check) can never leave the game believing the sound still plays. */
    private void watchdog() {
        final int gen = ++playGen;
        long ms = Math.max(0, (clip.getMicrosecondLength() - clip.getMicrosecondPosition()) / 1000) + 150;
        if (timer != null) timer.cancel();
        timer = new Timer(true);
        timer.schedule(new TimerTask() { public void run() {
            if (gen == playGen && state == STARTED) { AudioTrace.log(DesktopAudioPlayer.this, "watchdog end"); endOfMedia(); }
        } }, ms);
    }
    @Override protected void doStop() { playGen++; stoppedByUser = true; if (timer != null) timer.cancel(); if (clip != null) clip.stop(); }
    @Override protected void doDeallocate() { if (clip != null) clip.setFramePosition(0); }
    @Override protected void doClose() { playGen++; stoppedByUser = true; if (timer != null) timer.cancel(); if (clip != null) clip.close(); }
    @Override protected void doSetMediaTime(long us) { if (clip != null) clip.setMicrosecondPosition(us); }
    @Override protected long doGetMediaTime() { return clip == null ? 0 : clip.getMicrosecondPosition(); }
    @Override protected long doGetDuration() { return clip == null ? TIME_UNKNOWN : clip.getMicrosecondLength(); }
    @Override protected String doGetContentType() { return type == null ? "audio/x-wav" : type; }
    @Override protected void doSetVolume(int level) {
        if (clip == null) return;
        try {
            FloatControl c = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = level <= 0 ? c.getMinimum() : (float) (20 * Math.log10(level / 100.0));
            c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
        } catch (Exception ignored) {}
    }
}
