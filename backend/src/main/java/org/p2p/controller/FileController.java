package org.p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.p2p.service.FileSharer;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        this.uploadDir = System.getProperty("java.io.tmpdir")
                + File.separator + "peerlink-uploads";

        this.executorService = Executors.newFixedThreadPool(10);

        File f = new File(uploadDir);
        if (!f.exists()) f.mkdirs();

        server.createContext("/api/upload", new UploadHandler());
        server.createContext("/api/download", new DownloadHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/", new CORSHandler());

        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API running on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API stopped");
    }

    // ---------------- HEALTH -----------------
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            cors(exchange);
            byte[] res = "Backend OK".getBytes();
            exchange.sendResponseHeaders(200, res.length);
            exchange.getResponseBody().write(res);
            exchange.close();
        }
    }

    // ---------------- CORS fallback -----------------
    private class CORSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            cors(exchange);

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            byte[] res = "Not Found".getBytes();
            exchange.sendResponseHeaders(404, res.length);
            exchange.getResponseBody().write(res);
            exchange.close();
        }
    }

    // ---------------- MULTIPART PARSER (untouched) -----------------
    private static class MultipartParser {
        private final byte[] data;
        private final String boundary;

        public MultipartParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                byte[] boundaryBytes = ("--" + boundary).getBytes();
                byte[] endBoundary = ("--" + boundary + "--").getBytes();

                int start = indexOf(data, boundaryBytes, 0);
                if (start == -1) return null;
                start += boundaryBytes.length + 2;

                int headerEnd = indexOf(data, "\r\n\r\n".getBytes(), start);
                if (headerEnd == -1) return null;

                String header = new String(data, start, headerEnd - start);

                String filename = extractFilename(header);
                String contentType = extractContentType(header);

                int fileStart = headerEnd + 4;

                int fileEnd = indexOf(data, boundaryBytes, fileStart);
                if (fileEnd == -1)
                    fileEnd = indexOf(data, endBoundary, fileStart);

                if (fileEnd == -1) return null;

                if (data[fileEnd - 2] == '\r' && data[fileEnd - 1] == '\n')
                    fileEnd -= 2;

                byte[] fileBytes = Arrays.copyOfRange(data, fileStart, fileEnd);

                return new ParseResult(filename, contentType, fileBytes);

            } catch (Exception e) {
                return null;
            }
        }

        private static int indexOf(byte[] data, byte[] pattern, int start) {
            outer:
            for (int i = start; i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) continue outer;
                }
                return i;
            }
            return -1;
        }

        private static String extractFilename(String h) {
            String m = "filename=\"";
            int i = h.indexOf(m);
            if (i == -1) return "unnamed-file";
            i += m.length();
            int e = h.indexOf("\"", i);
            return (e == -1) ? "unnamed-file" : h.substring(i, e);
        }

        private static String extractContentType(String h) {
            String m = "Content-Type:";
            int i = h.indexOf(m);
            if (i == -1) return "application/octet-stream";
            i += m.length();
            int e = h.indexOf("\r\n", i);
            if (e == -1) e = h.length();
            return h.substring(i, e).trim();
        }

        public static class ParseResult {
            public final String filename;
            public final String contentType;
            public final byte[] file;

            public ParseResult(String f, String ct, byte[] fc) {
                filename = f; contentType = ct; file = fc;
            }
        }
    }

    // ---------------- UPLOAD -----------------
    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {

            cors(ex);

            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                respond(ex, 405, "Method not allowed");
                return;
            }

            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("boundary=")) {
                respond(ex, 400, "Invalid multipart Content-Type");
                return;
            }

            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            boundary = boundary.replace("\"", "").trim();

            byte[] body = ex.getRequestBody().readAllBytes();

            MultipartParser parser = new MultipartParser(body, boundary);
            MultipartParser.ParseResult result = parser.parse();

            if (result == null) {
                respond(ex, 400, "Multipart parsing failed");
                return;
            }

            String fileName = UUID.randomUUID() + "_" + result.filename;
            File out = new File(uploadDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(result.file);
            }

            int port = fileSharer.offerFile(out.getAbsolutePath());

            executorService.submit(() -> fileSharer.startFileServer(port));

            // ⭐ FIXED HERE ONLY
            String json = "{"
                    + "\"code\": \"" + port + "\","
                    + "\"filename\": \"" + result.filename + "\""
                    + "}";


            Headers h = ex.getResponseHeaders();
            h.add("Content-Type", "application/json");

            respond(ex, 200, json);
        }
    }

    // ---------------- DOWNLOAD -----------------
    private class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {

            cors(ex);

            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
                respond(ex, 405, "Method not allowed");
                return;
            }

            String query = ex.getRequestURI().getQuery();
            if (query == null) {
                respond(ex, 400, "Missing ?code=");
                return;
            }

            String[] parts = query.split("&");
            String codeStr = null;

            for (String p : parts) {
                if (p.startsWith("code=")) {
                    codeStr = p.substring(5);
                    break;
                }
            }

            if (codeStr == null || codeStr.isBlank()) {
                respond(ex, 400, "Missing ?code=");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(codeStr.trim());
            } catch (Exception e) {
                respond(ex, 400, "Invalid code");
                return;
            }

            try (Socket socket = new Socket("127.0.0.1", port);
                 InputStream in = socket.getInputStream()) {

                socket.setSoTimeout(5000);

                ByteArrayOutputStream header = new ByteArrayOutputStream();
                byte[] last4 = new byte[4];
                int c, i = 0;

                while ((c = in.read()) != -1) {
                    header.write(c);
                    last4[i % 4] = (byte) c;
                    i++;

                    if (i >= 4 &&
                            last4[(i - 4) % 4] == '\r' &&
                            last4[(i - 3) % 4] == '\n' &&
                            last4[(i - 2) % 4] == '\r' &&
                            last4[(i - 1) % 4] == '\n')
                        break;
                }

                String headerStr = header.toString();
                if (!headerStr.contains("Filename:")) {
                    respond(ex, 400, "Sender closed or invalid stream");
                    return;
                }

                String filename = headerStr.lines()
                        .filter(l -> l.startsWith("Filename:"))
                        .map(l -> l.substring(9).trim())
                        .findFirst().orElse("file");

                String type = headerStr.lines()
                        .filter(l -> l.startsWith("Content-Type:"))
                        .map(l -> l.substring(13).trim())
                        .findFirst().orElse("application/octet-stream");

                File tmp = File.createTempFile("peerlink-", ".tmp");

                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[4096];
                    int br;
                    while ((br = in.read(buf)) != -1)
                        fos.write(buf, 0, br);
                }

                Headers h = ex.getResponseHeaders();
                h.add("Content-Type", type);
                h.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                ex.sendResponseHeaders(200, tmp.length());

                try (OutputStream os = ex.getResponseBody();
                     FileInputStream fis = new FileInputStream(tmp)) {
                    byte[] buf = new byte[4096];
                    int br;
                    while ((br = fis.read(buf)) != -1)
                        os.write(buf, 0, br);
                }

                tmp.delete();

            } catch (Exception e) {
                respond(ex, 500, "Download error: " + e.getMessage());
            }
        }
    }

    private void cors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void respond(HttpExchange ex, int code, String msg) throws IOException {
        byte[] b = msg.getBytes();
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
