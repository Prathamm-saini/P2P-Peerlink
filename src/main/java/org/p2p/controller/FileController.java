package org.p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.p2p.service.FileSharer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executor;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";
        this.executor = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }
        //server.createContext("/upload",new UploadHandler());
        //server.createContext("/download",new DownloadHandler());
        server.createContext("/",new CORSHandler());
        server.setExecutor(executor);

    }
    public void start(){
        server.start();
        System.out.println("Server started on port " + server.getAddress().getPort());

    }
    public void stop(){
        server.stop(0);
        executor.shutdown();
        System.out.println("Server stopped on port " + server.getAddress().getPort());
    }
    private class CORSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE");
            headers.add("Access-Control-Allow-Headers","Content-Type, Authorization");

            if(exchange.getRequestMethod().equals("OPTIONS")){
                exchange.sendResponseHeaders(204,-1);
                return;
            }
            String response = "NOT FOUND";
            exchange.sendResponseHeaders(400,response.getBytes().length);
            try(OutputStream os = exchange.getResponseBody()){
                os.write(response.getBytes());
            }

        }
    }
}



