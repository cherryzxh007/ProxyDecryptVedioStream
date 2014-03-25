package com.leochin.proxydecryptvediostream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import android.os.Environment;
import android.util.Log;

public class LocalProxy implements Runnable {

	private static final String LOG_TAG = "wenhao";
	
	private static final int METHOD_NET = 0;
	private static final int METHOD_FILE = 1;
	private String serverUrl = "http://172.20.223.172/";

	private int port = 0;

	private boolean isRunning = true;
	private ServerSocket socket;
	private Thread thread;

	public int getPort() {
		return port;
	}

	/**
	 * 
	 * 初始化TCP Server
	 * 
	 */
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

	/**
	 * 
	 * 启动server
	 * 
	 */
	public void start() {

		if (socket == null) {
			throw new IllegalStateException(
					"Cannot start proxy; it has not been initialized.");
		}

		thread = new Thread(this);
		thread.start();
	}

	/**
	 * 
	 * 停止server
	 * 
	 */
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
				String url = readRequest(client);
				Log.i(LOG_TAG, "Purl = " + url);
				
				requestServer(client,url,METHOD_FILE);
				// processRequest(request, client);
			} catch (SocketTimeoutException e) {
				// Do nothing
			} catch (IOException e) {
				Log.e(LOG_TAG, "Error connecting to client", e);
			}
		}
		
		Log.d(LOG_TAG, "Proxy interrupted. Shutting down.");
	}

	private String readRequest(Socket client) {

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
		Log.d(LOG_TAG, "firstLine = " + firstLine);
		Log.d(LOG_TAG, "method = " + method + ":" + uri);

		return uri;
	}

	/**
	 * 
	 * 请求server
	 * 
	 * @param client
	 * @param url
	 */
	private void requestServer(Socket client, String filename, int method) {

		if(method == METHOD_NET){
			netMethod(client,filename);
		}else if (method == METHOD_FILE){
			fileMethod(client,filename);
		}
		
	}
	
	interface DecryptCallBack{
		byte[] decrypt(byte[] buffer, int offest , int len);
	}
	
	private DecryptCallBack mCallback;
	
	void setDecryptCallBack(DecryptCallBack callback){
		mCallback = callback;
	}
	
	
	/**
	 * 
	 * 文件的方式请求数据
	 * 
	 * @param client
	 * @param name
	 */
	private void fileMethod(Socket client, String name){
		String root = Environment.getExternalStorageDirectory().toString();
		File file = new File(root+"/"+name);
		
		FileInputStream fis = null;
		OutputStream os = null;
		if(file.exists()){
			try {
				fis = new FileInputStream(file);
				os = client.getOutputStream();
				
				byte[] buffer = new byte[1024];
				int len = 0;
				while((len = fis.read(buffer)) > 0){
					if(mCallback != null){
						buffer = mCallback.decrypt(buffer,0, len);
						len = buffer.length;
					}
					os.write(buffer, 0, len);				
				};
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally{
				try {
					if(client != null)
						client.close();	
					
					if(os != null)
						os.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
			
		}else{
			Log.d("wenhao","file not found...");
		}
		
	}
	
	/**
	 * 
	 * 网络的方式请求数据
	 * 
	 * @param client
	 * @param filename
	 */
	private void netMethod(Socket client, String filename){
		try {
			URL u = new URL(serverUrl + filename);
			HttpURLConnection conn = (HttpURLConnection) u.openConnection();
			OutputStream os = client.getOutputStream();
			InputStream is = conn.getInputStream();
			
			byte[] buffer = new byte[1024];
			int len = 0;
			while((len = is.read(buffer)) > 0){
				
				os.write(buffer, 0, len);				
			};
			
			client.close();
			conn.disconnect();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			
		}
	}

}
