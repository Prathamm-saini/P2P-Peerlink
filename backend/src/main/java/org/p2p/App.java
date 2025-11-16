package org.p2p;

import org.p2p.controller.FileController;

public class App {
    public static void main(String[] args) {
        try {
            FileController fileController = new FileController(8080);
            fileController.start();

            System.out.println("PeerLink server started on port 8080");
            System.out.println("Press Ctrl+C to exit");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping server...");
                fileController.stop();
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
