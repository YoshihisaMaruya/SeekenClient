package jp.ne.seeken.client;

import java.io.IOException;

import android.util.Log;
import jp.ne.seeken.serializer.RequestSerializer;
import jp.ne.seeken.serializer.ResponseSerializer;

public class ConvertDataFormatUsingOpenCV {
	// jni
	public native void getGrayByteArray(int width, int height, byte[] argb,
			byte[] gray);
	
	public native void saveGrayByteArray(int width,int height,byte[] gray,String save_path);

	static {
		System.loadLibrary("jni_part"); // ネイティブライブラリの読み込み
	}

	public ConvertDataFormatUsingOpenCV() {

	}

	/**
	 * リクエストを作成
	 * 
	 * @param width
	 * @param height
	 * @param argb
	 * @param color_format
	 * @return RequestSerializer
	 */
	public RequestSerializer makeRequest(int width, int height, byte[] argb,
			String color_format) {
		byte[] gray = new byte[width * height];
		this.getGrayByteArray(width, height, argb, gray);
		Log.i("request byte size", gray.length + "[B]");
		return new RequestSerializer(width, height, gray, "gray");
	}
	
	/**
	 * リスポンスのグレーイメージをローカルに保存
	 * @param width
	 * @param height
	 * @param gray_data
	 */
	public void saveResponse(int width,int height,byte[] gray,String save_path) throws IOException{
		saveGrayByteArray(width, height, gray, save_path);
	}
}
