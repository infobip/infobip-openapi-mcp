package com.infobip.openapi.mcp.auth.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Buffers everything the MCP transport writes without committing the real response, so the
 * {@link InitialAuthenticationFilter} can still write an authentication challenge instead. If the chain
 * commits itself via {@code sendError}/{@code sendRedirect}, the call is forwarded and
 * {@link #isDelegated()} reports that no buffered copy may follow.
 */
class BufferingMcpResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    private int status = SC_OK;

    private @Nullable String contentType;

    private String characterEncoding = StandardCharsets.UTF_8.name();

    private boolean usingWriter;

    private boolean usingOutputStream;

    private boolean encodingExplicitlySet;

    private @Nullable PrintWriter writer;

    private boolean delegated;

    BufferingMcpResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
    }

    @Override
    public @Nullable String getContentType() {
        return contentType;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
        this.encodingExplicitlySet = true;
    }

    boolean isEncodingExplicitlySet() {
        return encodingExplicitlySet;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setHeader(String name, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name, values);
    }

    @Override
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
    }

    @Override
    public void setDateHeader(String name, long timestamp) {
        addHeader(name, String.valueOf(timestamp));
    }

    @Override
    public @Nullable String getHeader(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Override
    public List<String> getHeaderNames() {
        return List.copyOf(headers.keySet());
    }

    @Override
    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name, List.of());
    }

    Map<String, List<String>> getHeadersSnapshot() {
        return headers;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (usingWriter) {
            throw new IllegalStateException("getWriter() has already been called on this response");
        }
        usingOutputStream = true;
        return new BufferedServletOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (usingOutputStream) {
            throw new IllegalStateException("getOutputStream() has already been called on this response");
        }
        usingWriter = true;
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(body, Charset.forName(characterEncoding)));
        }
        return writer;
    }

    @Override
    public void sendError(int sc) throws IOException {
        delegated = true;
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        delegated = true;
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        delegated = true;
        super.sendRedirect(location);
    }

    @Override
    public void flushBuffer() {
        // Swallowed on purpose - the real response must stay uncommitted until the filter decides.
    }

    boolean isDelegated() {
        return delegated;
    }

    byte[] getBody() {
        if (writer != null) {
            writer.flush();
        }
        return body.toByteArray();
    }

    private class BufferedServletOutputStream extends ServletOutputStream {

        @Override
        public void write(int b) {
            body.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            body.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            // No async writing is supported; everything is buffered in memory.
        }

        @Override
        public void flush() {
            // Swallowed on purpose - see flushBuffer().
        }
    }
}
