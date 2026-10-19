// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** What we know about a MIDlet suite: manifest (+ sibling .jad) properties, icon, MIDlet list. */
public final class GameInfo {
    public final Path jar;
    public final Map<String, String> props = new LinkedHashMap<>();
    public final List<String[]> midlets = new ArrayList<>();      // {name, icon, class}
    public byte[] iconBytes;

    private GameInfo(Path jar) { this.jar = jar; }

    public String name() { return props.getOrDefault("MIDlet-Name", stripExt(jar.getFileName().toString())); }
    public String vendor() { return props.getOrDefault("MIDlet-Vendor", ""); }
    public String version() { return props.getOrDefault("MIDlet-Version", ""); }
    /** Profile folder name: the sanitized MIDlet-Name, or the id stored in <jar>.id when that name is taken by another game. */
    public String id() {
        try {
            Path side = jar.resolveSibling(jar.getFileName().toString() + ".id");
            if (Files.isRegularFile(side)) { String v = Files.readString(side).trim(); if (!v.isEmpty()) return v; }
        } catch (IOException ignored) {}
        return Profile.idFor(name());
    }

    public static String hash8(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    public String mainClass(int index) {
        return index >= 1 && index <= midlets.size() ? midlets.get(index - 1)[2] : null;
    }
    private static String stripExt(String n) { int d = n.lastIndexOf('.'); return d > 0 ? n.substring(0, d) : n; }

    public static GameInfo read(Path jar) throws IOException {
        GameInfo g = new GameInfo(jar);
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest mf = jf.getManifest();
            if (mf == null) throw new IOException("JAR has no META-INF/MANIFEST.MF");
            for (Map.Entry<Object, Object> e : mf.getMainAttributes().entrySet()) g.props.put(e.getKey().toString(), e.getValue().toString());
        }
        // a .jad next to the jar (same base name) carries the authoritative descriptor
        Path jad = jar.resolveSibling(stripExt(jar.getFileName().toString()) + ".jad");
        if (Files.isRegularFile(jad)) {
            try { for (String line : Files.readAllLines(jad, StandardCharsets.UTF_8)) {
                int c = line.indexOf(':');
                if (c > 0) g.props.put(line.substring(0, c).trim(), line.substring(c + 1).trim());
            } } catch (IOException ignored) {}
        }
        for (int i = 1; i < 50; i++) {
            String v = g.props.get("MIDlet-" + i);
            if (v == null) break;
            String[] p = v.split(",", -1);
            if (p.length >= 3) g.midlets.add(new String[]{p[0].trim(), p[1].trim(), p[p.length - 1].trim()});
        }
        // icon: MIDlet-Icon, else icon field of MIDlet-1, else icon.png
        String icon = g.props.get("MIDlet-Icon");
        if ((icon == null || icon.isEmpty()) && !g.midlets.isEmpty()) icon = g.midlets.get(0)[1];
        try (ZipFile z = new ZipFile(jar.toFile())) {
            for (String cand : new String[]{icon, "icon.png", "/icon.png"}) {
                if (cand == null || cand.isEmpty()) continue;
                ZipEntry e = z.getEntry(cand.startsWith("/") ? cand.substring(1) : cand);
                if (e != null) { try (InputStream in = z.getInputStream(e)) { g.iconBytes = in.readAllBytes(); } break; }
            }
        }
        return g;
    }
}
