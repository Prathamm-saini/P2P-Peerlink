package org.p2p.service.FileSharer;

import org.p2p.utils.UploadUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

/**
 * The FileSharer class acts as a lightweight server module that allows a peer
 * to "offer" files to others in the network for downloading.
 * Conceptually:
 * 1. Each file that is offered is bound to a random available port.
 * 2. When another peer connects to that port, the file is sent.
 * 3. The FileSharer maintains a mapping between port numbers and file paths.
 */
public class FileSharer {

    // Mapping of port number -> file path for the files available for sharing
    private HashMap<Integer, String> availableFiles;

    /**
     * Constructor initializes an empty map of available files.
     */
    public FileSharer() {
        availableFiles = new HashMap<>();
        // At start, no files are offered
    }

    /**
     * offerFile():
     * - Used by a peer to make a file available to others.
     * - It generates a random (free) port using UploadUtils.generateCode().
     * - The file is associated with that port in the map.
     * - The function returns that port number, which can be shared with others.
     *
     * @param filePath Path of the file to be shared.
     * @return port number where the file can be accessed.
     */
    public int offerFile(String filePath) {
        int port;

        // Keep generating new ports until we find one not already used.
        while (true) {
            port = UploadUtils.generateCode(); // Random port generator utility

            // The condition below seems logically inverted in your version.
            // You should check: if port NOT already in map, then put it.
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    /**
     * startFileServer():
     * - Starts a server socket on the given port.
     * - Waits for a peer (client) to connect.
     * - Once a connection is received, it spawns a new thread
     *   (FileSenderHandler) to send the file.

     * port The port on which the file is to be served.
     */
    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);

        if (filePath == null) {
            System.out.println("No file is associated with the port " + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serving file: " + new File(filePath).getName() + " on port " + port);

            // Wait for an incoming connection
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            // Create a new thread to handle this file transfer
            new Thread(new FileSenderHandler(clientSocket, filePath)).start();

        } catch (IOException e) {
            System.out.println("Error occurred while trying to start FileServer on port " + port);
        }
    }

    /**
     * Inner class that handles actual file sending operation.
     * This is executed in a separate thread for concurrency.
     */
    private static class FileSenderHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            try (FileInputStream fileInputStream = new FileInputStream(filePath)) {

                // Stream to send data to the client
                OutputStream outputStream = clientSocket.getOutputStream();

                // Extract just the filename (without full path)
                String fileName = new File(filePath).getName();

                // Send a simple header/metadata before actual file bytes
                String header = "Filename: " + fileName + "\n";
                outputStream.write(header.getBytes());

                // Create a buffer for reading the file
                byte[] buffer = new byte[4096];  // 4KB buffer
                int bytesRead;

                // Keep reading from the file and writing to the socket
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                System.out.println("File " + fileName + " sent to " + clientSocket.getInetAddress());

            } catch (IOException e) {
                System.out.println("Error occurred while trying to send file: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }
}
