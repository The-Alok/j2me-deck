// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.rms;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * RMS backed by one file per record store under ~/.local/share/j2me-deck/rms/&lt;suite&gt;/.
 * Opening the same store twice returns the same shared instance (as on real handsets).
 */
public class RecordStore {
    public static final int AUTHMODE_PRIVATE = 0, AUTHMODE_ANY = 1;
    private static final Map<String, RecordStore> OPEN = new HashMap<>();

    private static Path root() {
        String suite = System.getProperty("j2me.suite", "default").replaceAll("[^A-Za-z0-9_.-]", "_");
        String base = System.getProperty("j2me.rms.dir");
        Path r = base != null ? Paths.get(base)
                : Paths.get(System.getProperty("user.home"), ".local", "share", "j2me-deck", "rms");
        return r.resolve(suite);
    }

    private final String name;
    private final Path file;
    private final TreeMap<Integer, byte[]> records = new TreeMap<>();
    private int nextId = 1, version = 0, openCount = 0;
    private long lastModified;
    private boolean closed;
    private final List<RecordListener> listeners = new ArrayList<>();

    private RecordStore(String name, Path file) { this.name = name; this.file = file; load(); }

    private static String safe(String s) { return s.replaceAll("[^A-Za-z0-9_.-]", "_"); }

    public static synchronized RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        if (name == null || name.isEmpty() || name.length() > 32) throw new IllegalArgumentException("record store name");
        try {
            Path dir = root();
            Files.createDirectories(dir);
            Path f = dir.resolve(safe(name) + ".rms");
            RecordStore rs = OPEN.get(f.toString());
            if (rs == null) {
                if (!create && !Files.exists(f)) throw new RecordStoreNotFoundException(name);
                rs = new RecordStore(name, f);
                if (!Files.exists(f)) rs.save();
                OPEN.put(f.toString(), rs);
            }
            rs.openCount++;
            rs.closed = false;
            return rs;
        } catch (IOException e) { throw new RecordStoreException(e.toString()); }
    }

    public static synchronized RecordStore openRecordStore(String name, boolean create, int auth, boolean writable) throws RecordStoreException {
        return openRecordStore(name, create);
    }

    public static synchronized void deleteRecordStore(String name) throws RecordStoreException {
        try {
            Path f = root().resolve(safe(name) + ".rms");
            RecordStore rs = OPEN.get(f.toString());
            if (rs != null && rs.openCount > 0) throw new RecordStoreException("record store is open");
            if (!Files.exists(f)) throw new RecordStoreNotFoundException(name);
            Files.delete(f);
            OPEN.remove(f.toString());
        } catch (IOException e) { throw new RecordStoreException(e.toString()); }
    }

    public static synchronized String[] listRecordStores() {
        try (var s = Files.list(root())) {
            String[] n = s.map(p -> p.getFileName().toString()).filter(x -> x.endsWith(".rms"))
                    .map(x -> x.substring(0, x.length() - 4)).toArray(String[]::new);
            return n.length == 0 ? null : n;
        } catch (IOException e) { return null; }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            nextId = in.readInt(); version = in.readInt(); lastModified = in.readLong();
            int n = in.readInt();
            for (int i = 0; i < n; i++) { int id = in.readInt(), len = in.readInt(); records.put(id, in.readNBytes(len)); }
        } catch (Exception e) {
            records.clear(); nextId = 1;
        }
    }

    private void save() throws RecordStoreException {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(nextId); out.writeInt(version); out.writeLong(lastModified);
                out.writeInt(records.size());
                for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                    out.writeInt(e.getKey()); out.writeInt(e.getValue().length); out.write(e.getValue());
                }
            }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new RecordStoreException(e.toString()); }
    }

    private void checkOpen() throws RecordStoreNotOpenException { if (closed) throw new RecordStoreNotOpenException(name); }
    private void touch() throws RecordStoreException { version++; lastModified = System.currentTimeMillis(); save(); }

    public synchronized void closeRecordStore() throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        if (--openCount <= 0) { openCount = 0; closed = true; listeners.clear(); }
    }
    public String getName() throws RecordStoreNotOpenException { checkOpen(); return name; }
    public synchronized int getVersion() throws RecordStoreNotOpenException { checkOpen(); return version; }
    public synchronized int getNumRecords() throws RecordStoreNotOpenException { checkOpen(); return records.size(); }
    public synchronized int getSize() throws RecordStoreNotOpenException {
        checkOpen(); int s = 0; for (byte[] b : records.values()) s += b.length; return s;
    }
    public int getSizeAvailable() throws RecordStoreNotOpenException { checkOpen(); return 64 * 1024 * 1024; }
    public synchronized long getLastModified() throws RecordStoreNotOpenException { checkOpen(); return lastModified; }
    public synchronized int getNextRecordID() throws RecordStoreNotOpenException, RecordStoreException { checkOpen(); return nextId; }

    public synchronized int addRecord(byte[] data, int off, int len) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        if (data == null && len > 0) throw new NullPointerException();
        int id = nextId++;
        records.put(id, data == null ? new byte[0] : Arrays.copyOfRange(data, off, off + len));
        touch();
        for (RecordListener l : new ArrayList<>(listeners)) l.recordAdded(this, id);
        return id;
    }
    public synchronized void deleteRecord(int id) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        if (records.remove(id) == null) throw new InvalidRecordIDException("record " + id);
        touch();
        for (RecordListener l : new ArrayList<>(listeners)) l.recordDeleted(this, id);
    }
    public synchronized int getRecordSize(int id) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen(); byte[] b = records.get(id); if (b == null) throw new InvalidRecordIDException("record " + id); return b.length;
    }
    public synchronized int getRecord(int id, byte[] buffer, int offset) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        byte[] b = getRecord(id);
        System.arraycopy(b, 0, buffer, offset, b.length);
        return b.length;
    }
    public synchronized byte[] getRecord(int id) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen(); byte[] b = records.get(id); if (b == null) throw new InvalidRecordIDException("record " + id); return b.clone();
    }
    public synchronized void setRecord(int id, byte[] data, int off, int len) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        if (!records.containsKey(id)) throw new InvalidRecordIDException("record " + id);
        records.put(id, data == null ? new byte[0] : Arrays.copyOfRange(data, off, off + len));
        touch();
        for (RecordListener l : new ArrayList<>(listeners)) l.recordChanged(this, id);
    }
    public void addRecordListener(RecordListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeRecordListener(RecordListener l) { listeners.remove(l); }

    public synchronized RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator cmp, boolean keepUpdated)
            throws RecordStoreNotOpenException {
        checkOpen();
        return new Enumeration(filter, cmp);
    }

    private final class Enumeration implements RecordEnumeration {
        private final RecordFilter filter; private final RecordComparator cmp;
        private List<Integer> ids = new ArrayList<>(); private int pos; private boolean destroyed;
        Enumeration(RecordFilter f, RecordComparator c) { filter = f; cmp = c; rebuild(); }
        public synchronized void rebuild() {
            List<Integer> list = new ArrayList<>();
            synchronized (RecordStore.this) {
                for (Map.Entry<Integer, byte[]> e : records.entrySet())
                    if (filter == null || filter.matches(e.getValue().clone())) list.add(e.getKey());
                if (cmp != null) list.sort((a, b) -> cmp.compare(records.get(a).clone(), records.get(b).clone()));
            }
            ids = list; pos = 0;
        }
        public int numRecords() { return ids.size(); }
        public boolean hasNextElement() { return pos < ids.size(); }
        public boolean hasPreviousElement() { return pos > 0; }
        public synchronized int nextRecordId() throws InvalidRecordIDException {
            if (pos >= ids.size()) throw new InvalidRecordIDException("no next"); return ids.get(pos++);
        }
        public synchronized int previousRecordId() throws InvalidRecordIDException {
            if (pos <= 0) throw new InvalidRecordIDException("no previous"); return ids.get(--pos);
        }
        public byte[] nextRecord() throws RecordStoreException { return getRecord(nextRecordId()); }
        public byte[] previousRecord() throws RecordStoreException { return getRecord(previousRecordId()); }
        public synchronized void reset() { pos = 0; }
        public void keepUpdated(boolean k) {}
        public boolean isKeptUpdated() { return false; }
        public void destroy() { destroyed = true; ids.clear(); }
    }
}
