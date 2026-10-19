// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Tiny class-file rewriter with no external dependencies. */
final class ResourceRewriter {
    private static final int MAGIC = 0xCAFEBABE;

    /** One call-site rewrite: owner.name(desc) -> helperOwner.helperName(helperDesc). */
    private static final class Rule {
        final String owner, name, desc, helperOwner, helperName, helperDesc;
        final boolean virtual, threadStatic;
        Rule(String owner, String name, String desc, boolean virtual, String ho, String hn, String hd, boolean threadStatic) {
            this.owner = owner; this.name = name; this.desc = desc; this.virtual = virtual;
            this.helperOwner = ho; this.helperName = hn; this.helperDesc = hd; this.threadStatic = threadStatic;
        }
    }
    private static final String COMPAT = "j2medeck/Compat", BRIDGE = "j2medeck/ResourceBridge";
    private static final Rule[] RULES = {
            new Rule("java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", true, BRIDGE, "getResourceAsStream", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;", false),
            new Rule("java/lang/System", "currentTimeMillis", "()J", false, COMPAT, "currentTimeMillis", "()J", false),
            new Rule("java/lang/System", "gc", "()V", false, COMPAT, "gc", "()V", false),
            new Rule("java/lang/Thread", "sleep", "(J)V", false, COMPAT, "sleep", "(J)V", true),
            new Rule("java/lang/Thread", "yield", "()V", false, COMPAT, "yield", "()V", true),
            new Rule("java/lang/Runtime", "gc", "()V", true, COMPAT, "gc", "(Ljava/lang/Runtime;)V", false),
            new Rule("java/lang/Runtime", "freeMemory", "()J", true, COMPAT, "freeMemory", "(Ljava/lang/Runtime;)J", false),
            new Rule("java/lang/Runtime", "totalMemory", "()J", true, COMPAT, "totalMemory", "(Ljava/lang/Runtime;)J", false),
    };
    private static final java.util.Set<String> PATCHABLE_NAMES = java.util.Set.of(
            "getResourceAsStream", "currentTimeMillis", "gc", "sleep", "yield", "freeMemory", "totalMemory");

    private ResourceRewriter() {}

    static byte[] rewrite(byte[] input) throws ClassNotFoundException {
        try {
            Parser p = new Parser(input);
            if (p.major > 52 || !p.mentionsPatchable()) return input;
            int patched = p.patchCode();
            if (patched == 0) return input;
            return p.serialize();
        } catch (IOException | RuntimeException e) {
            return input;   // rewriting is best-effort; the original class is still loadable
        }
    }

    private static final class Parser {
        final byte[] original;
        final List<Cp> cp = new ArrayList<>();
        final int major;
        final int cpEnd;
        byte[] body;
        final java.util.Map<Rule, Integer> helperRefs = new java.util.HashMap<>();
        String thisName, superName;

        Parser(byte[] bytes) throws IOException {
            original = bytes;
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) throw new IOException("bad magic");
            in.readUnsignedShort();
            major = in.readUnsignedShort();
            int count = in.readUnsignedShort();
            cp.add(null);
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                Cp x = new Cp(tag);
                switch (tag) {
                    case 1 -> {
                        int n = in.readUnsignedShort();
                        byte[] b = in.readNBytes(n);
                        if (b.length != n) throw new IOException("truncated utf8");
                        x.raw = b;
                        x.a = new String(b, StandardCharsets.UTF_8);
                    }
                    case 3, 4 -> x.aInt = in.readInt();
                    case 5, 6 -> {
                        x.aLong = in.readLong();
                        cp.add(x);
                        cp.add(null);
                        i++;
                        continue;
                    }
                    case 7, 8, 16, 19, 20 -> x.a = in.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> {
                        x.a = in.readUnsignedShort();
                        x.b = in.readUnsignedShort();
                    }
                    case 15 -> { x.a = in.readUnsignedByte(); x.b = in.readUnsignedShort(); }
                    default -> throw new IOException("unsupported constant pool tag " + tag);
                }
                cp.add(x);
            }
            cpEnd = bytes.length - in.available();
            body = java.util.Arrays.copyOfRange(bytes, cpEnd, bytes.length);
            thisName = className(u2(body, 2));
            superName = className(u2(body, 4));
        }

        boolean mentionsPatchable() {
            for (Cp c : cp) if (c != null && c.tag == 1 && PATCHABLE_NAMES.contains(c.a)) return true;
            return false;
        }

        /** Constant-pool Methodref of the helper for rule r (created once per class). */
        int helperRef(Rule r) {
            return helperRefs.computeIfAbsent(r, k -> {
                int clsOwner = addCp(new Cp(7, addUtf8(k.helperOwner)));
                int nat = addCp(new Cp(12, addUtf8(k.helperName), addUtf8(k.helperDesc)));
                return addCp(new Cp(10, clsOwner, nat));
            });
        }

        int addUtf8(String s) {
            return addCp(new Cp(1, s));
        }

        int addCp(Cp x) {
            cp.add(x);
            return cp.size() - 1;
        }

        int patchCode() throws IOException {
            Cursor c = new Cursor(body);
            c.skip(6); // access_flags, this_class, super_class
            int interfaces = c.u2();
            c.skip(2 * interfaces);
            int fields = c.u2();
            for (int i = 0; i < fields; i++) skipMember(c);
            int methods = c.u2();
            int patched = 0;
            for (int i = 0; i < methods; i++) {
                c.skip(2); // access_flags
                String methodName = utf(c.u2());
                String methodDesc = utf(c.u2());
                int attrs = c.u2();
                for (int j = 0; j < attrs; j++) {
                    int name = c.u2();
                    long len = c.u4();
                    int start = c.pos;
                    if ("Code".equals(utf(name))) {
                        int maxStack = c.u2();
                        int maxLocals = c.u2();
                        int codeLen = (int)c.u4();
                        int codeStart = c.pos;
                        patched += patchInstructions(codeStart, codeLen);
                        c.pos = codeStart + codeLen;
                        int ex = c.u2();
                        for (int ei = 0; ei < ex; ei++) {
                            c.u2(); // start_pc
                            c.u2(); // end_pc
                            int handlerPc = c.u2();
                            int catchType = c.u2();
                        }
                        int nested = c.u2();
                        for (int k = 0; k < nested; k++) {
                            c.skip(2);
                            long al = c.u4();
                            c.skip((int)al);
                        }
                        if (c.pos != start + len) c.pos = start + (int)len;
                    } else {
                        c.skip((int)len);
                    }
                }
            }
            return patched;
        }

        /**
         * Retarget calls listed in RULES to static helper methods. A virtual call becomes a static call whose first
         * parameter is the receiver; both forms are 3 bytes and have the same operand-stack effect, so no offsets,
         * branches or StackMapTable frames change.
         */
        int patchInstructions(int start, int len) {
            int end = start + len;
            int patched = 0;
            for (int p = start; p < end; ) {
                int op = body[p] & 0xff;
                if ((op == 0xb6 || op == 0xb8) && p + 2 < end) {
                    Rule r = match(op, u2(body, p + 1));
                    if (r != null) {
                        int ref = helperRef(r);
                        body[p] = (byte) 0xb8;                       // invokestatic
                        body[p + 1] = (byte) (ref >>> 8);
                        body[p + 2] = (byte) ref;
                        patched++;
                    }
                }
                int n = instructionLength(body, p, start);
                if (n <= 0 || p + n > end) break;
                p += n;
            }
            return patched;
        }

        Rule match(int opcode, int index) {
            if (index <= 0 || index >= cp.size()) return null;
            Cp m = cp.get(index);
            if (m == null || m.tag != 10) return null;
            String own = className((Integer) m.a);
            Cp nt = cp.get(m.b);
            if (own == null || nt == null || nt.tag != 12) return null;
            String name = utf((Integer) nt.a), desc = utf(nt.b);
            for (Rule r : RULES) {
                if (r.virtual != (opcode == 0xb6) || !r.name.equals(name) || !r.desc.equals(desc)) continue;
                if (r.owner.equals(own)) return r;
                // inside a Thread subclass, an unqualified sleep()/yield() is compiled with the subclass as owner
                if (r.threadStatic && own.equals(thisName) && "java/lang/Thread".equals(superName)) return r;
            }
            return null;
        }

        String className(int idx) {
            Cp c = cp.get(idx);
            return c != null && c.tag == 7 ? utf((Integer)c.a) : null;
        }

        String utf(int idx) {
            if (idx <= 0 || idx >= cp.size()) return null;
            Cp x = cp.get(idx);
            return x != null && x.tag == 1 ? (String)x.a : null;
        }

        byte[] serialize() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream(original.length + 128);
            DataOutputStreamEx w = new DataOutputStreamEx(out);
            w.u4(MAGIC);
            // Preserve original minor/major.
            w.u2(u2(original, 4));
            w.u2(u2(original, 6));
            w.u2(cp.size());
            for (int i = 1; i < cp.size(); i++) {
                Cp x = cp.get(i);
                if (x == null) continue;
                out.write(x.encode());
            }
            out.write(body);
            return out.toByteArray();
        }

        static void skipMember(Cursor c) throws IOException {
            c.skip(6);
            int attrs = c.u2();
            for (int j = 0; j < attrs; j++) { c.skip(2); c.skip((int)c.u4()); }
        }
    }

    private static final class Cp {
        final int tag;
        Object a;
        int b;
        long aLong;
        int aInt;

        byte[] raw;
        Cp(int tag) { this.tag = tag; }
        Cp(int tag, Object a) { this.tag = tag; this.a = a; if (tag == 1) raw = ((String) a).getBytes(StandardCharsets.UTF_8); }
        Cp(int tag, Object a, int b) { this.tag = tag; this.a = a; this.b = b; }

        byte[] encode() throws IOException {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write(tag);
            DataOutputStreamEx w = new DataOutputStreamEx(o);
            switch (tag) {
                case 1 -> { w.u2(raw.length); o.write(raw); }
                case 3, 4 -> w.u4(aInt);
                case 5, 6 -> w.u8(aLong);
                case 7, 8, 16, 19, 20 -> w.u2((Integer)a);
                case 9, 10, 11, 12, 17, 18 -> { w.u2((Integer)a); w.u2(b); }
                case 15 -> { w.u1((Integer)a); w.u2(b); }
                default -> throw new IOException("unsupported cp tag");
            }
            return o.toByteArray();
        }
    }

    private static final class Cursor {
        final byte[] a; int pos;
        Cursor(byte[] a) { this.a = a; }
        int u2() throws IOException { if(pos+2>a.length)throw new IOException("truncated"); int v=((a[pos]&255)<<8)|(a[pos+1]&255);pos+=2;return v; }
        long u4() throws IOException { long x=u2(); return (x<<16)|u2(); }
        void skip(int n) throws IOException { if(n<0||pos+n>a.length)throw new IOException("truncated");pos+=n; }
    }

    private static final class DataOutputStreamEx {
        final ByteArrayOutputStream out;
        DataOutputStreamEx(ByteArrayOutputStream out){this.out=out;}
        void u1(int x){out.write(x&255);}
        void u2(int x){out.write((x>>>8)&255);out.write(x&255);}
        void u4(long x){out.write((int)(x>>>24)&255);out.write((int)(x>>>16)&255);out.write((int)(x>>>8)&255);out.write((int)x&255);}
        void u8(long x){u4(x>>>32);u4(x);}
    }

    private static int instructionLength(byte[] c, int p, int codeStart) {
        int op = c[p] & 0xff;
        if (op == 0xaa) {
            int q = p + 1;
            int rel = q - codeStart;
            q += (4 - (rel & 3)) & 3;
            if (q + 12 > c.length) return 0;
            int low = (int) read4(c, q + 4);
            int high = (int) read4(c, q + 8);
            return (q + 12 + (high - low + 1) * 4) - p;
        }
        if (op == 0xab) {
            int q = p + 1;
            int rel = q - codeStart;
            q += (4 - (rel & 3)) & 3;
            if (q + 8 > c.length) return 0;
            int npairs = (int) read4(c, q + 4);
            return (q + 8 + npairs * 8) - p;
        }
        return switch (op) {
            case 0x10, 0x12, 0x15,0x16,0x17,0x18,0x19,0x36,0x37,0x38,0x39,0x3a,0xa9,0xbc -> 2;
            case 0x11,0x13,0x14,0x84,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xc6,0xc7 -> 3;
            case 0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xbb,0xbd,0xc0,0xc1 -> 3;   // getstatic..invokestatic, new, anewarray, checkcast, instanceof
            case 0xb9,0xba,0xc8,0xc9 -> 5;
            case 0xc5 -> 4;
            case 0xc4 -> ((c[p + 1] & 0xff) == 0x84) ? 6 : 4;
            default -> 1;
        };
    }

    private static long read4(byte[] a, int p) {
        return ((long)(a[p] & 255) << 24) | ((long)(a[p+1] & 255) << 16) | ((long)(a[p+2] & 255) << 8) | (a[p+3] & 255L);
    }

    private static int u2(byte[] a, int p) { return ((a[p]&255)<<8)|(a[p+1]&255); }
}
