// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Optional: converts formats Java Sound cannot read (MP3, AMR, AAC, ADPCM WAV...) to WAV using ffmpeg if installed. */
final class FfmpegDecoder {
    private static Boolean available;
    private static final Map<Integer, byte[]> CACHE = new LinkedHashMap<Integer, byte[]>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e) { return size() > 24; }
    };

    private FfmpegDecoder() {}

    static synchronized boolean available() {
        if (available == null) {
            available = false;
            String path = System.getenv("PATH");
            if (path != null) for (String d : path.split(File.pathSeparator)) if (new File(d, "ffmpeg").canExecute()) { available = true; break; }
        }
        return available;
    }

    /** @return WAV bytes, or null when ffmpeg is missing or the conversion failed. */
    static synchronized byte[] toWav(byte[] data) {
        if (!available()) return null;
        int key = Arrays.hashCode(data);
        byte[] hit = CACHE.get(key);
        if (hit != null) return hit;
        Path in = null, out = null;
        try {
            in = Files.createTempFile("j2me-in", ".bin");
            out = Files.createTempFile("j2me-out", ".wav");
            Files.write(in, data);
            Process p = new ProcessBuilder("ffmpeg", "-y", "-v", "error", "-i", in.toString(), "-f", "wav", "-acodec", "pcm_s16le", out.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0) return null;
            byte[] wav = Files.readAllBytes(out);
            if (wav.length < 64) return null;
            CACHE.put(key, wav);
            return wav;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) Files.deleteIfExists(in); if (out != null) Files.deleteIfExists(out); } catch (Exception ignored) {}
        }
    }
}
