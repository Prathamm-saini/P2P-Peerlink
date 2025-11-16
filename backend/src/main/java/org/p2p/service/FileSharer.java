package org.p2p.service;

import org.p2p.utils.UploadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSharer {

    // thread-safe maps
    private final Map<Integer, String> availableFiles = new ConcurrentHashMap<>();
    private final Map<Integer, String> fileNameMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> fileTypeMap = new ConcurrentHashMap<>();

    public void registerMetadata(int port, String filename, String contentType) {
        fileNameMap.put(port, filename);
        fileTypeMap.put(port, contentType);
    }

    public String getFilename(int port) {
        return fileNameMap.getOrDefault(port, "downloaded-file");
    }

    public String getFileType(int port) {
        return fileTypeMap.getOrDefault(port, "application/octet-stream");
    }

    public FileSharer() {
        // maps already initialized above
    }

    public int offerFile(String filePath) {
        int port;
        while (true) {
            port = UploadUtils.generateCode();

            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);

                // extract filename + content-type
                File f = new File(filePath);
                String filename = f.getName();
                String contentType;
                try {
                    contentType = Files.probeContentType(f.toPath());
                } catch (IOException e) {
                    contentType = "application/octet-stream";
                }
                if (contentType == null) contentType = "application/octet-stream";

                // store metadata for DownloadHandler
                registerMetadata(port, filename, contentType);

                return port;
            }
        }
    }

    /**
     * Start a short-lived ServerSocket that serves the file once,
     * then removes the mapping. This keeps behavior similar to your
     * original design but avoids leaving stale ports in the map.
     */
    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);

        if (filePath == null) {
            System.out.println("No file is associated with port " + port);
            return;
        }

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(0); // blocking accept; caller runs on worker thread
            System.out.println("Serving: " + new File(filePath).getName() + " on port " + port);

            // Accept exactly one client (keeps original behavior). If you want multiple downloads,
            // change this to a loop and don't remove the mapping until you want to expire it.
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            // pass the server port to the handler (not clientSocket.getLocalPort())
            new Thread(new FileSenderHandler(this, clientSocket, filePath, port)).start();

            // after starting handler, serverSocket will be closed in finally or after handler done
        } catch (IOException e) {
            System.out.println("Failed to start file server on port " + port + ": " + e.getMessage());
            // cleanup mapping so client doesn't keep using a dead port
            availableFiles.remove(port);
            fileNameMap.remove(port);
            fileTypeMap.remove(port);
        } finally {
            // Do not close serverSocket here — the handler may still be using the clientSocket.
            // The handler will close clientSocket when done.
            try {
                if (serverSocket != null && serverSocket.isClosed() == false) {
                    // close server socket after a short delay to allow handler to run (handler uses clientSocket).
                    // Alternatively, you may want to keep serverSocket open until handler finishes — but
                    // because we accepted a single client, it's okay to close here.
                    serverSocket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    private static class FileSenderHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;
        private final FileSharer fileSharer;
        private final int serverPort;

        public FileSenderHandler(FileSharer fileSharer, Socket clientSocket, String filePath, int serverPort) {
            this.fileSharer = fileSharer;
            this.clientSocket = clientSocket;
            this.filePath = filePath;
            this.serverPort = serverPort;
        }

        @Override
        public void run() {
            try (FileInputStream fileInputStream = new FileInputStream(filePath);
                 OutputStream outputStream = clientSocket.getOutputStream()) {

                // Use the serverPort passed into the handler to lookup metadata
                String originalName = fileSharer.getFilename(serverPort);
                String mimeType = fileSharer.getFileType(serverPort);

                // send a small header (custom protocol used by your DownloadHandler)
                String header =
                        "Filename: " + originalName + "\r\n" +
                                "Content-Type: " + mimeType + "\r\n\r\n";

                outputStream.write(header.getBytes());
                outputStream.flush();

                // small delay (original code had one) to make sure recipient read header
                try { Thread.sleep(25); } catch (InterruptedException ignored) {}

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();

                System.out.println("File " + originalName + " sent.");

            } catch (IOException e) {
                System.out.println("Error sending file: " + e.getMessage());
            } finally {
                // cleanup mapping after send (prevent stale re-use of the same port)
                fileSharer.availableFiles.remove(serverPort);
                fileSharer.fileNameMap.remove(serverPort);
                fileSharer.fileTypeMap.remove(serverPort);

                try {
                    clientSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
