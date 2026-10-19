// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** Compiles a game-like class, rewrites it, loads it and checks that the Compat shims really take effect. */
public class RewriterTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    public static class L extends ClassLoader {
        L(ClassLoader p) { super(p); }
        Class<?> define(String n, byte[] b) { return defineClass(n, b, 0, b.length); }
    }

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("rw");
        Files.writeString(dir.resolve("GameLike.java"),
            "public class GameLike {\n"
          + "  public static long now() { return System.currentTimeMillis(); }\n"
          + "  public static long timedSleep(long ms) throws Exception { long t = System.nanoTime(); Thread.sleep(ms); return (System.nanoTime() - t) / 1000000L; }\n"
          + "  public static long free() { return Runtime.getRuntime().freeMemory(); }\n"
          + "  public static long total() { return Runtime.getRuntime().totalMemory(); }\n"
          + "  public static void gcCalls() { System.gc(); Runtime.getRuntime().gc(); }\n"
          + "  public static void yields() { Thread.yield(); }\n"
          + "  public static class Worker extends Thread { public long took; public void run() { long t = System.nanoTime(); try { sleep(300); } catch (InterruptedException e) {} took = (System.nanoTime() - t) / 1000000L; } }\n"
          + "  public static long worker() throws Exception { Worker w = new Worker(); w.start(); w.join(); return w.took; }\n"
          + "}\n");
        int rc = com.sun.tools.javac.Main.compile(new String[]{"-nowarn", "-source", "8", "-target", "8", "-d", dir.toString(), dir.resolve("GameLike.java").toString()});
        if (rc != 0) throw new IllegalStateException("javac failed");

        Method rewrite = Class.forName("j2medeck.ResourceRewriter").getDeclaredMethod("rewrite", byte[].class);
        rewrite.setAccessible(true);
        L loader = new L(RewriterTest.class.getClassLoader());
        Class<?> w = null;
        for (String n : new String[]{"GameLike$Worker", "GameLike"}) {
            byte[] orig = Files.readAllBytes(dir.resolve(n + ".class"));
            byte[] re = (byte[]) rewrite.invoke(null, (Object) orig);
            check(re.length > orig.length, "rewriter patched " + n + " (" + orig.length + " -> " + re.length + " bytes)");
            Files.createDirectories(Path.of("/tmp/rwdbg"));
            Files.write(Path.of("/tmp/rwdbg/" + n.replace("$","_") + ".class"), re);
            Class<?> c = loader.define(n, re);
            if (n.equals("GameLike")) w = c;
        }
        Method now = w.getMethod("now"), sleep = w.getMethod("timedSleep", long.class), worker = w.getMethod("worker");

        Compat.reset();
        long t0 = (Long) now.invoke(null); Thread.sleep(200); long d1 = (Long) now.invoke(null) - t0;
        check(d1 >= 190 && d1 < 300, "speed 1x: game clock advances in real time (" + d1 + " ms for 200 ms)");

        Compat.setSpeed(4);
        t0 = (Long) now.invoke(null); Thread.sleep(200); long d2 = (Long) now.invoke(null) - t0;
        check(d2 >= 720 && d2 < 950, "speed 4x: game clock runs 4x (" + d2 + " ms for 200 ms)");
        long slept = (Long) sleep.invoke(null, 400L);
        check(slept >= 80 && slept < 160, "speed 4x: game sleep(400) takes ~100 real ms (" + slept + ")");
        long wk = (Long) worker.invoke(null);
        check(wk >= 55 && wk < 130, "speed 4x: unqualified sleep() inside a Thread subclass is scaled too (" + wk + " ms for 300)");

        Compat.setSpeed(0.5);
        slept = (Long) sleep.invoke(null, 100L);
        check(slept >= 190 && slept < 320, "speed 0.5x: sleep(100) takes ~200 ms (" + slept + ")");

        Compat.reset();
        Compat.pause();
        long p0 = (Long) now.invoke(null); Thread.sleep(150); long p1 = (Long) now.invoke(null);
        check(p0 == p1, "paused: game clock is frozen");
        final long[] blockedFor = {0};
        Thread t = new Thread(() -> { try { blockedFor[0] = (Long) sleep.invoke(null, 10L); } catch (Exception e) {} });
        long s = System.nanoTime(); t.start(); Thread.sleep(250); boolean stillBlocked = t.isAlive();
        Compat.resume(); t.join();
        check(stillBlocked, "paused: game sleep() blocks until resumed");
        long after = (Long) now.invoke(null);
        check(after - p1 < 100, "resume: clock continues from where it froze, not from the wall clock (" + (after - p1) + " ms later)");

        Compat.reset();
        Compat.ignoreSleep = true;
        long ig = (Long) sleep.invoke(null, 500L);
        check(ig < 50, "ignoreSleep: sleep(500) returns immediately (" + ig + " ms)");
        Compat.ignoreSleep = false;

        Compat.heapMb = 16;
        System.out.println("   heapMb now " + Compat.heapMb + ", direct shim total=" + (Compat.totalMemory(Runtime.getRuntime()) >> 10) + "KB");
        long total = (Long) w.getMethod("total").invoke(null), free = (Long) w.getMethod("free").invoke(null);
        System.out.println("   total=" + (total >> 10) + "KB free=" + (free >> 10) + "KB real total=" + (Runtime.getRuntime().totalMemory() >> 10) + "KB real free=" + (Runtime.getRuntime().freeMemory() >> 10) + "KB");
        check(total == 16L << 20 && free <= total && free >= 0, "heapmb=16: game sees a 16 MB heap, " + (free >> 10) + " KB free");
        Compat.reset();

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
