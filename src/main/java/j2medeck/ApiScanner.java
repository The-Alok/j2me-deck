// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;

/**
 * Static compatibility check: reads every class in a MIDlet JAR, collects the classes and members it references,
 * and compares them with what this runtime provides. No game code is executed.
 */
public final class ApiScanner {
    public enum Status { OK, PARTIAL, UNSUPPORTED }

    public static final class Report {
        public final Set<String> missingClasses = new TreeSet<>();
        public final Map<String, Set<String>> missingMembers = new TreeMap<>();
        public final Set<String> unsupportedFeatures = new LinkedHashSet<>();
        public final Set<String> notes = new LinkedHashSet<>();
        public int referencedApis;
        public Status status = Status.OK;

        public String summary() {
            switch (status) {
                case OK: return "Compatible" + (notes.isEmpty() ? "" : " (" + String.join(", ", notes) + ")");
                case PARTIAL: return "May have problems";
                default: return "Unsupported: " + String.join(", ", unsupportedFeatures);
            }
        }

        public String details() {
            StringBuilder sb = new StringBuilder();
            sb.append("Status: ").append(status).append("\nAPI classes referenced: ").append(referencedApis).append("\n");
            if (!notes.isEmpty()) { sb.append("\nUses (supported):\n"); for (String n : notes) sb.append("  \u2022 ").append(n).append("\n"); }
            if (!unsupportedFeatures.isEmpty()) { sb.append("\nUnsupported features:\n"); for (String n : unsupportedFeatures) sb.append("  \u2022 ").append(n).append("\n"); }
            if (!missingClasses.isEmpty()) { sb.append("\nMissing classes:\n"); for (String c : missingClasses) sb.append("  ").append(c).append("\n"); }
            if (!missingMembers.isEmpty()) {
                sb.append("\nMissing methods/fields:\n");
                for (Map.Entry<String, Set<String>> e : missingMembers.entrySet()) for (String m : e.getValue()) sb.append("  ").append(e.getKey()).append('.').append(m).append("\n");
            }
            if (missingClasses.isEmpty() && missingMembers.isEmpty()) sb.append("\nNo missing APIs found.\n");
            return sb.toString();
        }
    }

    private static final String[][] FEATURES = {
            // prefix, label, supported?
            {"javax.microedition.m3g", "3D graphics (M3G / JSR-184, software renderer)", "yes"},
            {"com.mascotcapsule", "Mascot Capsule 3D (software renderer)", "yes"},
            {"javax.bluetooth", "Bluetooth (JSR-82)", "no"},
            {"javax.obex", "OBEX", "no"},
            {"javax.wireless.messaging", "SMS / MMS messaging", "no"},
            {"javax.microedition.pim", "Contacts / calendar (PIM)", "no"},
            {"javax.microedition.location", "GPS location", "no"},
            {"javax.microedition.amms", "Advanced multimedia (AMMS)", "no"},
            {"javax.microedition.sip", "SIP", "no"},
            {"javax.microedition.lcdui.game", "Game API (Sprite/TiledLayer)", "yes"},
            {"com.nokia.mid.ui", "Nokia UI API", "yes"},
            {"com.nokia.mid.sound", "Nokia Sound API", "yes"},
            {"javax.microedition.io.file", "File access (JSR-75)", "yes"},
            {"javax.microedition.io.Http", "HTTP networking (enable per game)", "yes"},
            {"javax.microedition.io.Socket", "Sockets (enable per game)", "yes"},
            {"com.siemens", "Siemens API", "no"},
            {"com.samsung", "Samsung API", "no"},
            {"com.motorola", "Motorola API", "no"},
            {"com.vodafone", "Vodafone API", "no"},
            {"com.sprintpcs", "Sprint API", "no"},
            {"com.sonyericsson", "Sony Ericsson API", "no"},
            {"mmpp", "Mascot (mmpp)", "no"},
    };

    private ApiScanner() {}

    public static Report scan(File jar) throws IOException {
        Map<String, Set<String>> refs = new TreeMap<>();
        Set<String> own = new HashSet<>();
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : Collections.list(z.entries()))
                if (e.getName().endsWith(".class")) own.add(e.getName().replace('/', '.').replaceAll("\\.class$", ""));
            for (ZipEntry e : Collections.list(z.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                try (InputStream in = z.getInputStream(e)) { collect(in, refs); } catch (IOException | RuntimeException ex) { /* skip unreadable class */ }
            }
        }
        Report r = new Report();
        ClassLoader cl = ApiScanner.class.getClassLoader();
        for (Map.Entry<String, Set<String>> en : refs.entrySet()) {
            String c = en.getKey();
            if (own.contains(c)) continue;
            boolean jdk = c.startsWith("java.") || c.startsWith("javax.swing") || c.startsWith("sun.");
            if (!jdk && !c.contains(".")) continue;       // obfuscated/default-package helper that isn't in the jar: ignore
            if (!jdk) r.referencedApis++;
            Class<?> k;
            try { k = Class.forName(c, false, jdk ? null : cl); }
            catch (Throwable t) { r.missingClasses.add(c); continue; }
            if (jdk) continue;
            Set<String> have = members(k);
            for (String m : en.getValue()) if (!m.startsWith("<clinit>") && !have.contains(m)) r.missingMembers.computeIfAbsent(c, x -> new TreeSet<>()).add(pretty(m));
        }
        // features
        boolean fatal = false;
        for (String[] f : FEATURES) {
            boolean used = false;
            for (String c : refs.keySet()) if (c.startsWith(f[0])) { used = true; break; }
            if (!used) continue;
            if (f[2].equals("yes")) r.notes.add(f[1]);
            else { r.unsupportedFeatures.add(f[1]); fatal = true; }
        }
        if (fatal) r.status = Status.UNSUPPORTED;
        else if (!r.missingClasses.isEmpty() || !r.missingMembers.isEmpty()) r.status = Status.PARTIAL;
        return r;
    }

    private static String pretty(String m) { return m; }

    private static Set<String> members(Class<?> k) {
        Set<String> have = new HashSet<>();
        for (Class<?> x = k; x != null; x = x.getSuperclass()) {
            for (Method m : x.getDeclaredMethods()) have.add(m.getName() + " " + desc(m));
            for (Constructor<?> m : x.getDeclaredConstructors()) have.add("<init> " + desc(m));
            for (Field f : x.getDeclaredFields()) have.add(f.getName() + " " + d(f.getType()));
            for (Class<?> i : ifaces(x)) for (Method m : i.getDeclaredMethods()) have.add(m.getName() + " " + desc(m));
        }
        return have;
    }
    private static Set<Class<?>> ifaces(Class<?> k) {
        Set<Class<?>> s = new HashSet<>();
        for (Class<?> i : k.getInterfaces()) { s.add(i); s.addAll(ifaces(i)); }
        return s;
    }
    private static String d(Class<?> c) {
        if (c == void.class) return "V"; if (c == int.class) return "I"; if (c == long.class) return "J"; if (c == boolean.class) return "Z";
        if (c == byte.class) return "B"; if (c == char.class) return "C"; if (c == short.class) return "S"; if (c == float.class) return "F";
        if (c == double.class) return "D"; if (c.isArray()) return "[" + d(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }
    private static String desc(Executable m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(d(p));
        return sb.append(")").append(m instanceof Method ? d(((Method) m).getReturnType()) : "V").toString();
    }

    /** Minimal class-file reader: records class references and (owner, name+descriptor) member references. */
    private static void collect(InputStream is, Map<String, Set<String>> refs) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(is));
        if (in.readInt() != 0xCAFEBABE) return;
        in.readUnsignedShort(); in.readUnsignedShort();
        int n = in.readUnsignedShort();
        Object[] cp = new Object[n];
        for (int i = 1; i < n; i++) {
            int t = in.readUnsignedByte();
            switch (t) {
                case 1: cp[i] = in.readUTF(); break;
                case 3: case 4: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 7: case 8: case 16: case 19: case 20: cp[i] = new int[]{t, in.readUnsignedShort()}; break;
                case 9: case 10: case 11: case 12: case 17: case 18: cp[i] = new int[]{t, in.readUnsignedShort(), in.readUnsignedShort()}; break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                default: throw new IOException("bad constant pool tag " + t);
            }
        }
        for (int i = 1; i < n; i++) {
            if (!(cp[i] instanceof int[])) continue;
            int[] r = (int[]) cp[i];
            if (r[0] == 7) {
                Object o = cp[r[1]];
                if (o instanceof String && !((String) o).startsWith("[")) refs.computeIfAbsent(((String) o).replace('/', '.'), k -> new TreeSet<>());
            } else if (r[0] == 9 || r[0] == 10 || r[0] == 11) {
                int[] cr = (int[]) cp[r[1]], nt = (int[]) cp[r[2]];
                String owner = ((String) cp[cr[1]]).replace('/', '.');
                if (owner.startsWith("[")) continue;
                refs.computeIfAbsent(owner, k -> new TreeSet<>()).add(cp[nt[1]] + " " + cp[nt[2]]);
            }
        }
    }

    /** CLI: java -cp loader.jar j2medeck.ApiScanner game.jar [...] */
    public static void main(String[] args) throws Exception {
        for (String a : args) { System.out.println("== " + a); System.out.println(scan(new File(a)).details()); }
    }
}
