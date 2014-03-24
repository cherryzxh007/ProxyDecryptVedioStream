package com.leochin.proxydecryptvediostream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHttpRequest;

import android.os.Environment;
import android.util.Log;

public class StreamProxy implements Runnable{
	private static final String LOG_TAG = StreamProxy.class.getName();

    private int port = 0;

    private boolean isRunning = true;
    private ServerSocket socket;
    private Thread thread;
 
    public int getPort() {
        return port;
    }

    public void init() {
        try {
            socket = new ServerSocket(0, 0,
                    InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }));
            socket.setSoTimeout(5000);
            port = socket.getLocalPort();
            Log.d(LOG_TAG, "port " + port + " obtained");
        } catch (UnknownHostException e) {
            Log.e(LOG_TAG, "Error initializing server", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error initializing server", e);
        }
    }

    public void start() {

        if (socket == null) {
            throw new IllegalStateException(
                    "Cannot start proxy; it has not been initialized.");
        }

        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        isRunning = false;

        if (thread == null) {
            throw new IllegalStateException(
                    "Cannot stop proxy; it has not been started.");
        }

        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, "running");
        while (isRunning) {
            try {
                Socket client = socket.accept();
                if (client == null) {
                    continue;
                }
                Log.d(LOG_TAG, "client connected");
                HttpRequest request = readRequest(client);
                processRequest(request, client);
            } catch (SocketTimeoutException e) {
                // Do nothing
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error connecting to client", e);
            }
        }
        Log.d(LOG_TAG, "Proxy interrupted. Shutting down.");
    }

    private HttpRequest readRequest(Socket client) {
        HttpRequest request = null;

        InputStream is;
        String firstLine;
        BufferedReader reader;

        Log.e(LOG_TAG, "ThreadName:" + Thread.currentThread().getName());
        try {
            is = client.getInputStream();
            reader = new BufferedReader(new InputStreamReader(is), 8192);
            firstLine = reader.readLine();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error parsing request", e);
            return null;
        }

        if (firstLine == null) {
            Log.i(LOG_TAG, "Proxy client closed connection without a request.");
            return null;
        }

        // decide method and uri
        StringTokenizer st = new StringTokenizer(firstLine);
        String method = st.nextToken();
        String uri = st.nextToken().substring(1);
        // create result
        request = new BasicHttpRequest(method, uri);
        // rest
        while (true) {
            try {
                firstLine = reader.readLine();
                if (firstLine == null || firstLine.trim().compareTo("") == 0)
                    break;

                int p = firstLine.indexOf(':');
                if (p < 0)
                    continue;
                final String atr = firstLine.substring(0, p).trim()
                        .toLowerCase();
                final String val = firstLine.substring(p + 1).trim();
                Log.d(LOG_TAG, "header:" + atr + ":" + val);
                request.setHeader(atr, val);

            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        return request;
    }

    private void processRequest(HttpRequest request, Socket client)
            throws IllegalStateException, IOException {
        EncryptFile inputfile;
        if (request == null) {
            Log.d(LOG_TAG, "request load fail");
            return;
        }
        Log.d(LOG_TAG, "processing");
        // open file
        String filepath = Environment.getExternalStorageDirectory() + "/"
                + request.getRequestLine().getUri();
        // s

        try {
            inputfile = new EncryptFile(filepath);
        } catch (Exception e1) {
            Log.d(LOG_TAG, "EncryptFile load fail");
            e1.printStackTrace();
            return;
        }
        String mimetype = inputfile.getMimeType();
        long filesize = inputfile.getFileSize();

        Log.d(LOG_TAG, "filepath:" + filepath);
        Log.d(LOG_TAG, "mimetype:" + mimetype);
        Log.d(LOG_TAG, "filesize:" + filesize);

        String headers;
        // 跳转播放哦

        if (request.getHeaders("range").length != 0
                && request.getHeaders("range")[0].getValue().startsWith(
                        "bytes=")) {
            String range = request.getHeaders("range")[0].getValue().substring(
                    6);

            long startFrom = 0, endAt = -1;
            int minus = range.indexOf('-');
            if (minus > 0) {
                try {
                    String startR = range.substring(0, minus);
                    startFrom = Long.parseLong(startR);
                    String endR = range.substring(minus + 1);
                    endAt = Long.parseLong(endR);
                } catch (NumberFormatException nfe) {
                }
            }
            if (endAt <= 0)
                endAt = filesize;
            // seek file
            try {
                inputfile.seekTo(startFrom);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(LOG_TAG, "EncryptFile seek fail");
                return;
            }
            // header

            headers = "HTTP/1.1 206 OK\r\n";
            headers += "Content-Type: " + mimetype + "\r\n";
            headers += "Accept-Ranges: bytes\r\n";
            headers += "Content-Length: " + (filesize - startFrom) + "\r\n";
            headers += "Content-Range: bytes " + startFrom + "-" + endAt + "/"
                    + filesize + "\r\n";
            headers += "\r\n";
            Log.d(LOG_TAG, "accept:range 206 request");

        } else {
            // 从头播放哦

            // header
            headers = "HTTP/1.1 200 OK\r\n";
            headers += "Content-Type: " + mimetype + "\r\n";
            headers += "Accept-Ranges: bytes\r\n";
            headers += "Content-Length: " + filesize + "\r\n";
            headers += "\r\n";
            Log.d(LOG_TAG, "accept:start 200 request");
        }
        Log.d(LOG_TAG, "respone_headers:" + headers);
         //这里应该输出视频内容，略
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } finally {
            if (inputfile != null) {
                inputfile.close();
                inputfile = null;
            }
            client.close();
        }
}
