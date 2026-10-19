// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io.file;

import javax.microedition.io.Connection;
import javax.microedition.io.ConnectionNotFoundException;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * JSR-75 FileConnection mapped into a sandbox: file:///root1/dir/x.dat is
 * ~/.local/share/j2me-deck/fs/&lt;game&gt;/root1/dir/x.dat so games can't touch the rest of the disk and
 * different games don't overwrite each other's files.
 */
public final class FileConnectionImpl implements FileConnection {
    static File sandbox() {
        String suite = System.getProperty("j2me.suite", "default").replaceAll("[^A-Za-z0-9_.-]", "_");
        String base = System.getProperty("j2me.fs.dir");
        File f = base != null ? new File(base, suite)
                : new File(System.getProperty("user.home"), ".local/share/j2me-deck/fs/" + suite);
        f.mkdirs();
        return f;
    }

    private String url;          // file:///root1/dir/name
    private File file;
    private boolean open = true;

    private FileConnectionImpl(String url) throws IOException { setTo(url); }

    public static Connection open(String url, int mode) throws IOException {
        if (!url.startsWith("file://")) throw new ConnectionNotFoundException(url);
        // Legacy behaviour used by older builds of this loader: file:///absolute/path is allowed when it is outside the sandbox roots
        return new FileConnectionImpl(url);
    }

    private void setTo(String u) throws IOException {
        String path = u.substring("file://".length());
        if (path.startsWith("localhost")) path = path.substring("localhost".length());
        if (!path.startsWith("/")) path = "/" + path;
        if (path.contains("..")) throw new IllegalArgumentException("invalid path");
        url = "file://" + path;
        String rel = path.substring(1);
        file = rel.isEmpty() ? sandbox() : new File(sandbox(), rel);
    }

    private void ensureOpen() throws IOException { if (!open) throw new IOException("connection closed"); }
    private boolean isRootListing() { return url.equals("file:///"); }

    public boolean isOpen() { return open; }
    public InputStream openInputStream() throws IOException {
        ensureOpen();
        if (!file.exists()) throw new IOException("file does not exist: " + url);
        return new FileInputStream(file);
    }
    public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
    public OutputStream openOutputStream() throws IOException {
        ensureOpen();
        if (!file.exists()) throw new IOException("file does not exist (call create() first): " + url);
        return new FileOutputStream(file);
    }
    public OutputStream openOutputStream(long off) throws IOException {
        ensureOpen();
        if (!file.exists()) throw new IOException("file does not exist");
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(off);
        return new OutputStream() {
            public void write(int b) throws IOException { raf.write(b); }
            public void write(byte[] b, int o, int l) throws IOException { raf.write(b, o, l); }
            public void close() throws IOException { raf.close(); }
        };
    }
    public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }
    public long totalSize() { return file.getTotalSpace(); }
    public long availableSize() { return file.getUsableSpace(); }
    public long usedSize() { return directorySizeQuiet(sandbox()); }
    private long directorySizeQuiet(File d) {
        long n = 0; File[] k = d.listFiles();
        if (k != null) for (File f : k) n += f.isDirectory() ? directorySizeQuiet(f) : f.length();
        return n;
    }
    public long directorySize(boolean sub) throws IOException {
        if (!file.isDirectory()) throw new IOException("not a directory");
        long n = 0; File[] k = file.listFiles();
        if (k != null) for (File f : k) n += f.isDirectory() ? (sub ? directorySizeQuiet(f) : 0) : f.length();
        return n;
    }
    public long fileSize() throws IOException { if (file.isDirectory()) return -1; if (!file.exists()) throw new IOException("no such file"); return file.length(); }
    public boolean canRead() { return file.canRead(); }
    public boolean canWrite() { return file.canWrite(); }
    public boolean isHidden() { return file.getName().startsWith("."); }
    public void setReadable(boolean r) throws IOException { file.setReadable(r); }
    public void setWritable(boolean w) throws IOException { file.setWritable(w); }
    public void setHidden(boolean h) throws IOException {}
    public Enumeration list() throws IOException { return list("*", true); }
    public Enumeration list(String filter, boolean hidden) throws IOException {
        ensureOpen();
        Vector<String> v = new Vector<>();
        if (isRootListing()) { for (Enumeration e = FileSystemRegistry.listRoots(); e.hasMoreElements(); ) v.add((String) e.nextElement()); return v.elements(); }
        if (!file.isDirectory()) throw new IOException("not a directory");
        Pattern p = Pattern.compile(("\\Q" + (filter == null ? "*" : filter).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "\\E"));
        File[] kids = file.listFiles();
        if (kids != null) {
            Arrays.sort(kids);
            for (File f : kids) {
                if (!hidden && f.getName().startsWith(".")) continue;
                if (p.matcher(f.getName()).matches()) v.add(f.getName() + (f.isDirectory() ? "/" : ""));
            }
        }
        return v.elements();
    }
    public void create() throws IOException {
        ensureOpen();
        if (file.exists()) throw new IOException("already exists");
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();            // be lenient: real phones fail, games assume success
        if (!file.createNewFile()) throw new IOException("cannot create " + url);
    }
    public void mkdir() throws IOException { ensureOpen(); if (!file.mkdirs() && !file.isDirectory()) throw new IOException("cannot create directory"); }
    public boolean exists() { return isRootListing() || file.exists(); }
    public boolean isDirectory() { return isRootListing() || file.isDirectory(); }
    public void delete() throws IOException { ensureOpen(); if (!file.delete()) throw new IOException("cannot delete " + url); }
    public void rename(String newName) throws IOException {
        ensureOpen();
        File to = new File(file.getParentFile(), newName.replace("/", ""));
        if (!file.renameTo(to)) throw new IOException("cannot rename");
        String base = url.substring(0, url.lastIndexOf('/') + 1);
        setTo(base + to.getName());
    }
    public void truncate(long off) throws IOException { try (RandomAccessFile r = new RandomAccessFile(file, "rw")) { r.setLength(off); } }
    public long lastModified() { return file.lastModified(); }
    public String getName() { String n = file.getName(); return isRootListing() ? "" : n + (file.isDirectory() ? "/" : ""); }
    public String getPath() { String p = url.substring("file://".length()); int i = p.lastIndexOf('/', p.length() - (p.endsWith("/") ? 2 : 1)); return i < 0 ? "/" : p.substring(0, i + 1); }
    public String getURL() { return url; }
    public void setFileConnection(String name) throws IOException {
        String base = url.endsWith("/") ? url : url.substring(0, url.lastIndexOf('/') + 1);
        setTo(name.equals("..") ? base.substring(0, base.lastIndexOf('/', base.length() - 2) + 1) : base + name);
    }
    public void close() { open = false; }
}
