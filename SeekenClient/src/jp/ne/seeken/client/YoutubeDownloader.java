package jp.ne.seeken.client;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;

import android.util.Log;


/**
 * 
 * @author maruya
 */
public class YoutubeDownloader {

	private Socket socket = null;
	private DataOutputStream out = null;
	private InputStream in = null;
	private OutputStream result = null;
	
	private String host;
	private int port;
	/**
	 * @param args
	 */
	public YoutubeDownloader(String host,int port){
		this.host = host;
		this.port = port;
	}
	
	/**
	 * Socket情報をセット
	 * @param host
	 * @param port
	 */
	private void setSocket(){
		try {
			this.socket = new Socket(host,port);
			 this.out = new DataOutputStream(socket.getOutputStream());
			 this.in = socket.getInputStream();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * コネクションのクローズ
	 */
	public void close(){
		try {
			this.socket.close();
			this.in.close();
			this.out.close();
			this.result.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * ダウンロードを実行
	 * @param id
	 * @param output_path
	 */
	public void execute(int id,String output_path){
		this.setSocket();
		try {
			this.result = new FileOutputStream(new File(output_path));
			
			//ダウンロードIDを送信
			this.out.writeInt(id);
			
			byte[] buf = new byte[1024];
			while(true){
				int len = this.in.read(buf);
				if(len > 0){
					this.result.write(buf,0,len);
				}
				else break;
			}
			this.result.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
