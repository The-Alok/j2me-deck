// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.io;
import java.io.IOException;
public interface HttpConnection extends ContentConnection {
    String HEAD = "HEAD", GET = "GET", POST = "POST";
    int HTTP_OK = 200, HTTP_CREATED = 201, HTTP_ACCEPTED = 202, HTTP_NOT_AUTHORITATIVE = 203, HTTP_NO_CONTENT = 204,
            HTTP_RESET = 205, HTTP_PARTIAL = 206, HTTP_MULT_CHOICE = 300, HTTP_MOVED_PERM = 301, HTTP_MOVED_TEMP = 302,
            HTTP_SEE_OTHER = 303, HTTP_NOT_MODIFIED = 304, HTTP_USE_PROXY = 305, HTTP_TEMP_REDIRECT = 307,
            HTTP_BAD_REQUEST = 400, HTTP_UNAUTHORIZED = 401, HTTP_PAYMENT_REQUIRED = 402, HTTP_FORBIDDEN = 403,
            HTTP_NOT_FOUND = 404, HTTP_BAD_METHOD = 405, HTTP_NOT_ACCEPTABLE = 406, HTTP_PROXY_AUTH = 407,
            HTTP_CLIENT_TIMEOUT = 408, HTTP_CONFLICT = 409, HTTP_GONE = 410, HTTP_LENGTH_REQUIRED = 411,
            HTTP_PRECON_FAILED = 412, HTTP_ENTITY_TOO_LARGE = 413, HTTP_REQ_TOO_LONG = 414, HTTP_UNSUPPORTED_TYPE = 415,
            HTTP_UNSUPPORTED_RANGE = 416, HTTP_EXPECT_FAILED = 417, HTTP_INTERNAL_ERROR = 500, HTTP_NOT_IMPLEMENTED = 501,
            HTTP_BAD_GATEWAY = 502, HTTP_UNAVAILABLE = 503, HTTP_GATEWAY_TIMEOUT = 504, HTTP_VERSION = 505;
    String getURL();
    String getProtocol();
    String getHost();
    String getFile();
    String getRef();
    String getQuery();
    int getPort();
    String getRequestMethod();
    void setRequestMethod(String method) throws IOException;
    String getRequestProperty(String key);
    void setRequestProperty(String key, String value) throws IOException;
    int getResponseCode() throws IOException;
    String getResponseMessage() throws IOException;
    long getExpiration() throws IOException;
    long getDate() throws IOException;
    long getLastModified() throws IOException;
    String getHeaderField(String name) throws IOException;
    String getHeaderField(int n) throws IOException;
    String getHeaderFieldKey(int n) throws IOException;
    int getHeaderFieldInt(String name, int def) throws IOException;
    long getHeaderFieldDate(String name, long def) throws IOException;
}
