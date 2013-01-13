package jp.ne.seeken.client;

import java.lang.Thread;
import java.util.LinkedList;
import java.util.Queue;
import java.net.*;
import java.io.*;

import jp.ne.seeken.serializer.*;

import android.util.Log;

public class CommunicationThread extends Thread {

	private Queue<RequestSerializer> requestBuffer = new LinkedList<RequestSerializer>(); //requset用のバッファ
	private final int max_request_buffer = 2;
	private Queue<ResponseSerializer> responseBuffer = new LinkedList<ResponseSerializer>(); //response用のバッファ
	private final int max_response_buffer = 2; //response bufferの最大数

	private int set_count = 1; //setImgが呼ばれた回数 
	private final int request_count = 3; //サーバーにリクエストを投げるset_countの回数

	//通信用
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private Boolean is_close = false;
	
	private String host;
	private int port;
	

	/**
	 * 
	 * @param host
	 * @param port
	 */
	public CommunicationThread(String host,int port) {
		this.host = host;
		this.port = port;
	}

	
	/**
	 * 
	 * @param host
	 * @param port
	 */
	private void setSocket() {
		try {
			this.socket = new Socket(host, port);
			this.out = new ObjectOutputStream(this.socket.getOutputStream());
			this.in = new ObjectInputStream(this.socket.getInputStream());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * TODO: スレッドと同期したほうが良い　？
	 */
	public void clearBuffer(){
		this.requestBuffer.clear();
		this.responseBuffer.clear();
	}
	/**
	 * 
	 * @param widht
	 * @param height
	 * @param data
	 * @param color_format
	 */
	public void push(RequestSerializer rs) {
		if(this.max_request_buffer < requestBuffer.size()){
			this.requestBuffer.poll();
		}
		this.requestBuffer.add(rs);
	}
	

	public ResponseSerializer pull() {
		//Log.i("GetResponse","GetResponse");
		if(this.responseBuffer.size() == 0) return null;
		else return this.responseBuffer.poll(); 
	}

	public void close() {
		this.is_close = true;
	}

	@Override
	public void run() {
		this.setSocket();
		try {
			RequestSerializer request;
			while (!is_close) {
				if (this.requestBuffer.size() == 0) continue; // send bufferがないときは何も送らない

				request = this.requestBuffer.poll();
				
				
				Log.i("Connection Strat","Connection Start");
				
				 //送信にかかった時間を測定
				long start = System.currentTimeMillis();
				
				this.out.writeObject(request); //send
				this.out.flush();
				
				//resetをしないと、OutOfMemoryが発生
				//ObjectOutputStreamは一度writeObjectしたオブジェクトを内部テーブルにキャッシュして保持しつづける仕様らしい。
				this.out.reset();
				
				Log.i("Request Time", (System.currentTimeMillis() - start) + "[ms]");
				
				//送信完了してから、受信が終わるまでの時間を測定　
				start = System.currentTimeMillis(); 
				ResponseSerializer response = (ResponseSerializer)this.in.readObject(); //response
				long stop = System.currentTimeMillis();
				
				Log.i("Response Time", (stop - start) + "[ms]");
				
				 //nullがぞうが見つからなかった時 or 更新の必要がないとき
				if (response != null){
					if ( (max_response_buffer -1 ) < this.responseBuffer.size()) {
						this.responseBuffer.poll();
					}
					//TODO: TMP
					this.responseBuffer.add(response);
				}
				Log.i("ResponseBufferSize",responseBuffer.size() + "");
			}
			this.out.writeObject(null); //コネクションをcloseするためのメッセージ
			this.out.close();
		    this.in.close();
			this.socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
