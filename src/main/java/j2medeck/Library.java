// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** The user's game collection: jars are copied into ~/.local/share/j2me-deck/games and indexed by file name. */
public final class Library {
    public static final Path GAMES = Paths.get(System.getProperty("user.home"), ".local", "share", "j2me-deck", "games");

    private Library() {}

    public static List<GameInfo> list() {
        List<GameInfo> out = new ArrayList<>();
        try (var s = Files.list(GAMES)) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".jar")).sorted().forEach(p -> {
                try { out.add(GameInfo.read(p)); } catch (IOException e) { System.err.println("[library] skipping " + p + ": " + e.getMessage()); }
            });
        } catch (IOException ignored) {}
        out.sort(Comparator.comparing(g -> g.name().toLowerCase()));
        return out;
    }

    /** Copy a .jar (and its .jad if present) into the library. A .jad argument imports the jar next to it. */
    public static GameInfo add(Path source) throws IOException {
        String n = source.getFileName().toString();
        if (n.toLowerCase().endsWith(".jad")) {
            Path jar = source.resolveSibling(n.substring(0, n.length() - 4) + ".jar");
            if (!Files.isRegularFile(jar)) throw new IOException("No matching .jar next to " + n);
            return add(jar);
        }
        if (!n.toLowerCase().endsWith(".jar")) throw new IOException("Not a .jar file: " + n);
        GameInfo probe = GameInfo.read(source);          // validates manifest before copying
        Files.createDirectories(GAMES);
        String base = probe.id();
        Path dest = GAMES.resolve(base + ".jar");
        // same file already imported?
        if (Files.exists(dest) && Files.size(dest) == Files.size(source)) return GameInfo.read(dest);
        int k = 2;
        while (Files.exists(dest)) dest = GAMES.resolve(base + "-" + k++ + ".jar");
        Files.copy(source, dest);
        // two different games with the same MIDlet-Name must not share settings or key mappings
        for (GameInfo other : list()) {
            if (!other.jar.equals(dest) && other.id().equals(base) && !GameInfo.hash8(other.jar).equals(GameInfo.hash8(dest))) {
                Files.writeString(dest.resolveSibling(dest.getFileName() + ".id"), base + "-" + GameInfo.hash8(dest));
                break;
            }
        }
        Path jad = source.resolveSibling(n.substring(0, n.length() - 4) + ".jad");
        if (Files.isRegularFile(jad)) Files.copy(jad, dest.resolveSibling(dest.getFileName().toString().replaceAll("\\.jar$", ".jad")), StandardCopyOption.REPLACE_EXISTING);
        return GameInfo.read(dest);
    }

    /** Import one file, or every .jar in a folder. @return games added. */
    public static List<GameInfo> addAll(Path p) throws IOException {
        List<GameInfo> added = new ArrayList<>();
        if (Files.isDirectory(p)) {
            try (var s = Files.walk(p, 3)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().toLowerCase().endsWith(".jar"))::iterator) {
                    try { added.add(add(f)); } catch (IOException e) { System.err.println("[library] " + f + ": " + e.getMessage()); }
                }
            }
        } else added.add(add(p));
        return added;
    }

    public static final Path LOGS = Paths.get(System.getProperty("user.home"), ".local", "share", "j2me-deck", "logs");

    /** Log file of the last run of g (the previous run is kept as .old). */
    public static Path logFile(GameInfo g) { return LOGS.resolve(g.id() + ".log"); }

    public static Path rotateLog(GameInfo g) throws IOException {
        Files.createDirectories(LOGS);
        Path log = logFile(g);
        if (Files.exists(log)) Files.move(log, LOGS.resolve(g.id() + ".old.log"), StandardCopyOption.REPLACE_EXISTING);
        return log;
    }

    public static void remove(GameInfo g, boolean deleteSaves) throws IOException {
        Files.deleteIfExists(g.jar.resolveSibling(g.jar.getFileName() + ".id"));
        Files.deleteIfExists(g.jar);
        Files.deleteIfExists(g.jar.resolveSibling(g.jar.getFileName().toString().replaceAll("\\.jar$", ".jad")));
        if (deleteSaves) {
            String suite = (g.vendor().isEmpty() ? "unknown" : g.vendor()) + "_" + g.name();
            String safe = suite.replaceAll("[^A-Za-z0-9_.-]", "_");
            for (String root : new String[]{"rms", "fs"}) deleteTree(Paths.get(System.getProperty("user.home"), ".local", "share", "j2me-deck", root, safe));
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) { for (Path f : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(f); }
    }
}
