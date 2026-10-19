// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io;

import j2medeck.Settings;
import javax.microedition.io.file.FileConnectionImpl;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Generic Connection Framework. file:// is sandboxed under ~/.local/share/j2me-deck/fs; http/https/socket
 * only work when the game's profile has "network" enabled (many old games phone home for ads and hang otherwise).
 */
public final class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;

    private Connector() {}

    public static Connection open(String name) throws IOException { return open(name, READ_WRITE, false); }
    public static Connection open(String name, int mode) throws IOException { return open(name, mode, false); }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name == null) throw new IllegalArgumentException("name");
        int c = name.indexOf(':');
        if (c <= 0) throw new IllegalArgumentException("Missing protocol: " + name);
        String scheme = name.substring(0, c).toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "file": return FileConnectionImpl.open(name, mode);
            case "http": case "https": requireNetwork(name); return new HttpImpl(name, mode);
            case "socket": requireNetwork(name); return new SocketImpl(name);
            default: throw new ConnectionNotFoundException("Unsupported protocol: " + scheme);
        }
    }

    private static void requireNetwork(String name) throws ConnectionNotFoundException {
        if (!Settings.network) throw new ConnectionNotFoundException("Network access is disabled for this game (" + name + ")");
    }

    public static InputStream openInputStream(String name) throws IOException {
        Connection c = open(name, READ);
        return new FilterInputStream(((InputConnection) c).openInputStream()) {
            @Override public void close() throws IOException { try { super.close(); } finally { c.close(); } }
        };
    }
    public static DataInputStream openDataInputStream(String name) throws IOException { return new DataInputStream(openInputStream(name)); }
    public static OutputStream openOutputStream(String name) throws IOException {
        Connection c = open(name, WRITE);
        return new FilterOutputStream(((OutputConnection) c).openOutputStream()) {
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
            @Override public void close() throws IOException { try { super.close(); } finally { c.close(); } }
        };
    }
    public static DataOutputStream openDataOutputStream(String name) throws IOException { return new DataOutputStream(openOutputStream(name)); }

    // ------------------------------------------------------------------ http
    private static final class HttpImpl implements HttpConnection {
        private final String url; private final URL u; private HttpURLConnection c; private String method = GET;
        private final Map<String, String> props = new LinkedHashMap<>();
        private ByteArrayOutputStream body; private boolean sent; private String[] keys, values;

        HttpImpl(String url, int mode) throws IOException {
            this.url = url;
            try { u = new URL(url); } catch (MalformedURLException e) { throw new IllegalArgumentException(e.getMessage()); }
        }
        private void connect() throws IOException {
            if (sent) return;
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000);
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> e : props.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            if (body != null && !method.equals(GET) && !method.equals(HEAD)) {
                c.setDoOutput(true);
                try (OutputStream o = c.getOutputStream()) { body.writeTo(o); }
            }
            c.connect();
            sent = true;
            List<String> k = new ArrayList<>(), v = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet())
                for (String val : e.getValue()) { k.add(e.getKey()); v.add(val); }
            keys = k.toArray(new String[0]); values = v.toArray(new String[0]);
        }
        public String getURL() { return url; }
        public String getProtocol() { return u.getProtocol(); }
        public String getHost() { return u.getHost(); }
        public String getFile() { return u.getPath(); }
        public String getRef() { return u.getRef(); }
        public String getQuery() { return u.getQuery(); }
        public int getPort() { return u.getPort() < 0 ? u.getDefaultPort() : u.getPort(); }
        public String getRequestMethod() { return method; }
        public void setRequestMethod(String m) throws IOException {
            if (sent) throw new IOException("already connected");
            if (!m.equals(GET) && !m.equals(POST) && !m.equals(HEAD)) throw new IOException("bad method " + m);
            method = m;
        }
        public String getRequestProperty(String k) { return props.get(k); }
        public void setRequestProperty(String k, String v) throws IOException { if (sent) throw new IOException("already connected"); props.put(k, v); }
        public int getResponseCode() throws IOException { connect(); return c.getResponseCode(); }
        public String getResponseMessage() throws IOException { connect(); return c.getResponseMessage(); }
        public long getExpiration() throws IOException { connect(); return c.getExpiration(); }
        public long getDate() throws IOException { connect(); return c.getDate(); }
        public long getLastModified() throws IOException { connect(); return c.getLastModified(); }
        public String getHeaderField(String n) throws IOException { connect(); return c.getHeaderField(n); }
        public String getHeaderField(int n) throws IOException { connect(); return n >= 0 && n < values.length ? values[n] : null; }
        public String getHeaderFieldKey(int n) throws IOException { connect(); return n >= 0 && n < keys.length ? keys[n] : null; }
        public int getHeaderFieldInt(String n, int d) throws IOException { connect(); return c.getHeaderFieldInt(n, d); }
        public long getHeaderFieldDate(String n, long d) throws IOException { connect(); return c.getHeaderFieldDate(n, d); }
        public String getType() { try { connect(); return c.getContentType(); } catch (IOException e) { return null; } }
        public String getEncoding() { try { connect(); return c.getContentEncoding(); } catch (IOException e) { return null; } }
        public long getLength() { try { connect(); return c.getContentLengthLong(); } catch (IOException e) { return -1; } }
        public InputStream openInputStream() throws IOException {
            connect();
            return c.getResponseCode() >= 400 ? c.getErrorStream() == null ? new ByteArrayInputStream(new byte[0]) : c.getErrorStream() : c.getInputStream();
        }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        public OutputStream openOutputStream() throws IOException {
            if (sent) throw new IOException("already connected");
            if (body == null) body = new ByteArrayOutputStream();
            if (method.equals(GET)) method = POST;       // writing a body implies POST on most handsets
            return body;
        }
        public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }
        public void close() { if (c != null) c.disconnect(); }
    }

    // ------------------------------------------------------------------ socket
    private static final class SocketImpl implements SocketConnection {
        private final Socket s = new Socket();
        SocketImpl(String url) throws IOException {
            String rest = url.substring("socket://".length());
            int colon = rest.lastIndexOf(':');
            if (colon < 0) throw new IllegalArgumentException("socket URL needs host:port");
            String host = rest.substring(0, colon);
            int port = Integer.parseInt(rest.substring(colon + 1));
            if (host.isEmpty()) throw new ConnectionNotFoundException("server sockets are not supported");
            s.connect(new InetSocketAddress(host, port), 15000);
        }
        public void setSocketOption(byte o, int v) throws IOException {
            switch (o) {
                case DELAY: s.setTcpNoDelay(v == 0); break;
                case LINGER: s.setSoLinger(v > 0, v); break;
                case KEEPALIVE: s.setKeepAlive(v != 0); break;
                case RCVBUF: s.setReceiveBufferSize(v); break;
                case SNDBUF: s.setSendBufferSize(v); break;
                default: throw new IllegalArgumentException("option " + o);
            }
        }
        public int getSocketOption(byte o) throws IOException {
            switch (o) {
                case DELAY: return s.getTcpNoDelay() ? 0 : 1;
                case LINGER: return Math.max(0, s.getSoLinger());
                case KEEPALIVE: return s.getKeepAlive() ? 1 : 0;
                case RCVBUF: return s.getReceiveBufferSize();
                case SNDBUF: return s.getSendBufferSize();
                default: throw new IllegalArgumentException("option " + o);
            }
        }
        public String getLocalAddress() { return s.getLocalAddress().getHostAddress(); }
        public int getLocalPort() { return s.getLocalPort(); }
        public String getAddress() { return s.getInetAddress().getHostAddress(); }
        public int getPort() { return s.getPort(); }
        public InputStream openInputStream() throws IOException { return s.getInputStream(); }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(s.getInputStream()); }
        public OutputStream openOutputStream() throws IOException { return s.getOutputStream(); }
        public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(s.getOutputStream()); }
        public void close() throws IOException { s.close(); }
    }
}
