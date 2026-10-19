// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Favourites, last-played time and play counts for the library UI ({@code ~/.config/j2me-deck/library-state.properties}). */
public final class LibraryState {
    private static final Properties P = new Properties();
    private static Path loadedFrom;
    /** GameInfo.id() looks for a side-car file, so look it up once per GameInfo instead of on every paint. */
    private static final java.util.Map<GameInfo, String> IDS = new java.util.WeakHashMap<>();
    private static String id(GameInfo g) { return IDS.computeIfAbsent(g, GameInfo::id); }

    private LibraryState() {}

    private static Path file() { return Profile.CONFIG.resolve("library-state.properties"); }

    private static synchronized void ensure() {
        Path f = file();
        if (f.equals(loadedFrom)) return;
        P.clear();
        loadedFrom = f;
        try (InputStream in = Files.newInputStream(f)) { P.load(in); } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) { P.store(out, "J2ME Deck library state"); }
        } catch (IOException ignored) {}
    }

    public static synchronized boolean isFavorite(GameInfo g) { ensure(); return P.getProperty("fav." + id(g)) != null; }

    public static synchronized boolean toggleFavorite(GameInfo g) {
        ensure();
        boolean now = P.getProperty("fav." + id(g)) == null;
        if (now) P.setProperty("fav." + id(g), "1"); else P.remove("fav." + id(g));
        save();
        return now;
    }

    public static synchronized void markPlayed(GameInfo g) {
        ensure();
        P.setProperty("last." + id(g), String.valueOf(System.currentTimeMillis()));
        P.setProperty("plays." + id(g), String.valueOf(plays(g) + 1));
        save();
    }

    /** Epoch millis of the last launch, or 0 when never played. */
    public static synchronized long lastPlayed(GameInfo g) {
        ensure();
        try { return Long.parseLong(P.getProperty("last." + id(g), "0")); } catch (NumberFormatException e) { return 0; }
    }

    public static synchronized int plays(GameInfo g) {
        ensure();
        try { return Integer.parseInt(P.getProperty("plays." + id(g), "0")); } catch (NumberFormatException e) { return 0; }
    }

    /** "just now", "3 days ago", ... for a timestamp; "never" for 0. */
    public static String ago(long when) {
        if (when <= 0) return "never";
        long s = Math.max(0, (System.currentTimeMillis() - when) / 1000);
        if (s < 90) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + (s / 3600 == 1 ? " hour ago" : " hours ago");
        if (s < 86400L * 14) return (s / 86400) + (s / 86400 == 1 ? " day ago" : " days ago");
        if (s < 86400L * 60) return (s / (86400 * 7)) + " weeks ago";
        return (s / (86400 * 30)) + " months ago";
    }
}
