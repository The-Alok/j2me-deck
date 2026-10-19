// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.InputStream;

/**
 * Suite-scoped resource lookup. CLDC's Class.getResourceAsStream() reads from the MIDlet JAR no matter which
 * class it is called on (old games do "".getClass().getResourceAsStream(...)); on Java SE that only works for
 * classes defined by the suite's class loader. ResourceRewriter redirects those calls here.
 */
public final class ResourceBridge {
    private static volatile ClassLoader loader;

    private ResourceBridge() {}

    public static void setLoader(ClassLoader value) { loader = value; }

    public static InputStream getResourceAsStream(String name) {
        if (name == null) return null;
        String key = name.replace('\\', '/');
        while (key.startsWith("/")) key = key.substring(1);
        ClassLoader cl = loader;
        if (cl == null) cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            InputStream in = cl.getResourceAsStream(key);
            if (in != null) return in;
        }
        return ClassLoader.getSystemResourceAsStream(key);
    }

    /** Target of rewritten Class.getResourceAsStream(String) calls. */
    public static InputStream getResourceAsStream(Class<?> c, String name) {
        if (name == null) throw new NullPointerException("name");
        String n = name.replace('\\', '/');
        if (!n.startsWith("/") && c != null && c.getClassLoader() == loader) {
            String cn = c.getName();
            int dot = cn.lastIndexOf('.');
            if (dot > 0) n = cn.substring(0, dot).replace('.', '/') + "/" + n;   // relative to the class's package
        }
        return getResourceAsStream(n);
    }
}
