package jp.ne.seeken.client.metaio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.io.FileWriter;

import jp.ne.seeken.client.CommunicationThread;
import jp.ne.seeken.client.ConvertDataFormatUsingOpenCV;
import jp.ne.seeken.client.metaio.R;
import jp.ne.seeken.client.YoutubeDownloader;
import jp.ne.seeken.client.metaio.R.layout;
import jp.ne.seeken.serializer.RequestSerializer;
import jp.ne.seeken.serializer.ResponseSerializer;

import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.ECOLOR_FORMAT;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.Rotation;
import com.metaio.sdk.jni.TrackingValues;
import com.metaio.sdk.jni.TrackingValuesVector;
import com.metaio.sdk.jni.Vector3d;
import com.metaio.tools.io.AssetsManager;

import android.R.anim;
import android.os.Bundle;
import android.os.Environment;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.View;
import android.graphics.*;

//OpenCV
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;

public class SeekenClietnActivity extends MetaioSDKViewActivity {

	private String packageName = null;
	private String dir_path = null;
	private String traking_data_path = null;
	// ムービ用のプレーン
	private IGeometry[] moviePlanes = new IGeometry[5];

	private Properties prop = new Properties();

	private MetaioSDKCallbackHandler mCallbackHandler;

	// Seeken
	private CommunicationThread ct;
	private YoutubeDownloader yd;
	private ConvertDataFormatUsingOpenCV convertDataFormatUsingOpenCV;

	//
	private int camera_count = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// extract all the assets
		try {
			AssetsManager.extractAllAssets(getApplicationContext(), true);
		} catch (IOException e) {
			MetaioDebug.printStackTrace(Log.ERROR, e);
		}

		super.onCreate(savedInstanceState);

		// データ保存用のパス
		// dir_pathを入れ替えると、SDカード保存になる。
		/*this.dir_path = Environment.getExternalStorageDirectory().getPath() + "/seeken"; // /dataなど 
		File f = new File(dir_path); f.mkdir();
		this.dir_path += "/";*/
		 
		this.packageName = this.getApplicationContext().getPackageName();
		this.dir_path = "/data/data/" + packageName + "/files/";

		this.traking_data_path = this.dir_path + "TrackingData.xml";

		// config情報
		AssetManager as = getResources().getAssets();
		InputStream is = null;
		try {
			is = as.open("config.properties");
			prop.load(is);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// //各種スレッド
		// Seeken DB通信用スレッド
		String host = prop.get("seeken.host").toString();
		int port = Integer.parseInt(prop.get("seeken.port").toString());
		ct = new CommunicationThread(host, port);
		ct.start();

		// Youube Download用
		this.yd = new YoutubeDownloader(host, port + 1);

		// リスポンスデータ作成用
		this.convertDataFormatUsingOpenCV = new ConvertDataFormatUsingOpenCV();

		mCallbackHandler = new MetaioSDKCallbackHandler();

		camera_count = 0;
	}

	@Override
	protected int getGUILayout() {
		// TODO: return 0 in case of no GUI overlay
		return R.layout.activity_seeken_clietn;
	}

	@Override
	protected void onStart() {
		super.onStart();

		// hide GUI until SDK is ready
		if (!mRendererInitialized)
			mGUIView.setVisibility(View.GONE);
	}

	@Override
	protected void loadContent() {
		// コールバックのセット
		UnifeyeCallbackHandler ch = new UnifeyeCallbackHandler();
		metaioSDK.registerCallback(ch);

		// 画像取得スレッドのスタート
		this.tw = new TrackingWatcher();
		this.tw.start();
	}

	@Override
	protected void onPause() {
		is_run = false;
		super.onPause();
	}

	/**
	 * リスポンス情報をローカルに保存 TODO: I/O処理がとてつもなく遅い。高速化する部分
	 * 
	 * @param rs
	 */
	private int[] id_maps = null;

	private void savaResponse(ResponseSerializer rs) {

		// trakingDataの保存
		String trackingData = rs.getXML();
		try {
			Log.v("save_path",traking_data_path);
			FileWriter fw = new FileWriter(traking_data_path);
			fw.write(trackingData);
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// id_mapsを取得
		int[] pre_id_maps = id_maps;
		id_maps = rs.getIdMaps();
		
		for(int i = 0; i < id_maps.length; i++){
			Log.d("result id",id_maps[i] + "");
		}

		// いらなくなった画像,yotube動画の消去
		if (pre_id_maps != null) {
			for (int pre_id : pre_id_maps) {
				boolean is_delete = true;
				for (int id : id_maps) {
					if (pre_id == id)
						is_delete = false;
				}
				if (is_delete) {
					File f = new File(this.getImagePath(pre_id));
					f.delete();
					f = new File(this.getMoviePath(pre_id));
					f.delete();
				}
			}
		}

		// metaio geometryリソースを一旦開放
		for (int i = 0; i < moviePlanes.length; i++) {
			if (moviePlanes[i] != null) {
				metaioSDK.unloadGeometry(moviePlanes[i]);
			}
			moviePlanes[i] = null;
		}

		// 新規画像保存
		for (int i = 0; i < id_maps.length; i++) {
			byte[] image = rs.getImage(i);
			// imageがローカルになければ、ローカルに保存
			if (image != null) {
				try {
					this.convertDataFormatUsingOpenCV.saveResponse(rs.getWidth(i), rs.getHeight(i), image,this.getImagePath(id_maps[i]));
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
		}
		}
	}

	private String getImagePath(int id) {
		return dir_path + id + ".jpg";
	}

	private String getMoviePath(int id) {
		return dir_path + id + ".3g2";
	}

	@Override
	public void onSurfaceDestroyed() {
		this.deleteResoce();
		this.is_run = false;
		super.onSurfaceDestroyed();
	}

	/**
	 * 画像、動画リソースの消去
	 */
	private void deleteResoce() {
		if (id_maps != null) {
			for (int id : id_maps) {
				File f = new File(this.getImagePath(id));
				f.delete();
				f = new File(this.getMoviePath(id));
				f.delete();
			}
		}
	}

	/**
	 * トラッキングレート分、スレッドをとめる
	 */
	private void waitTrackingFrameRate() {
		// トラッキングレートの分だけ待ってみる
		try {
			float trackingFrameRate = metaioSDK.getTrackingFrameRate();
			float sleep_mtime = (1 / trackingFrameRate) * 2 * 1000;
			Log.i("waitTrakingFrame",sleep_mtime + "[ms]");
			Thread.sleep((long) sleep_mtime);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * トラッキングしている数を監視するスレッド。トラッキングを感知したら、直ちにリソースの変更を停止。アクションをセット。
	 * スレッドを停止させる為にはis_runフラグをfalseに
	 */
	Boolean is_run = true;
	TrackingWatcher tw;

	private class TrackingWatcher extends Thread {
		@Override
		public void run() {
			ResponseSerializer rs;
		
			while (is_run) {
				// TODO : xmlファイルをセットしていなくても動くのか？
				long total_start = System.currentTimeMillis();
				int gnd = metaioSDK.getNumberOfValidCoordinateSystems(); // トラッキングしている数
				// //トラッキングを開始したら、
				if (gnd != 0) {
					Log.i("Tracking","true");
					// //トラッキングデータをセット
					TrackingValuesVector trackingValues = metaioSDK
							.getTrackingValues();
					Log.i("traking size",trackingValues.size() + "");
					if(trackingValues.size() == 0) continue;
					for (int i = 0; i < trackingValues.size(); i++) {
						final TrackingValues v = trackingValues.get(i);
						int id = Integer.valueOf(v.getCosName());
						int cosId = v.getCoordinateSystemID();
						String moviePath = getMoviePath(id);
						File f = new File(moviePath);
						boolean fb = f.exists();
						// ファイルが存在しているときは、ダウンロードしない
						if (!f.exists()){
							yd.execute(id, moviePath);
						}
						moviePlanes[cosId - 1] = metaioSDK
								.createGeometryFromMovie(moviePath);
						moviePlanes[cosId - 1].setScale(new Vector3d(2.0f,
								2.0f, 2.0f));
						moviePlanes[cosId - 1].setRotation(new Rotation(0f, 0f,
								0f));
						moviePlanes[cosId - 1].setCoordinateSystemID(cosId);
						moviePlanes[cosId - 1].setVisible(true);
						moviePlanes[cosId - 1].startMovieTexture(true);
					}

					// バッファをクリア
					ct.clearBuffer();

					// トラッキングが終了するまで、待つ
					while (true) {
						waitTrackingFrameRate();
						gnd = metaioSDK.getNumberOfValidCoordinateSystems(); // トラッキングしている数
						if (gnd == 0)
							break;
					}
					Log.i("Traking","finish");
				} else {
					Log.i("Tracking","false");
					// トラッキングの停止
					metaioSDK.pauseTracking();

					// 応答があるまで待つ(TrackingDataがないと、トラッキングを開始してもしても意味がない)
					metaioSDK.requestCameraImage();

					//metaioの使用上、カメラで1回目に取得したデータは無視
					camera_count++;
					if (camera_count > 1) {
						while ((rs = ct.pull()) == null);

						// 保存
						long save_start = System.currentTimeMillis(); // 時間測定用
						savaResponse(rs);
						Log.i("Save local file time",
								(System.currentTimeMillis() - save_start) + "[ms]");

						// トラッキングファイルをセット
						long taking_config_start = System.currentTimeMillis(); // 時間測定用
						
						boolean trakingConfig = metaioSDK
								.setTrackingConfiguration(traking_data_path);
						
						Log.i("Tracking Config Time",
								(System.currentTimeMillis() - taking_config_start) + "[ms]");

						if (!trakingConfig) {
							Log.i("TrackingWatcher TrakingConfig", "false");
						}
					}
					// トラッキングの再開
					metaioSDK.resumeTracking();

					// トラッキングが始まるように、フレームレート分待ってやる
					waitTrackingFrameRate();
					
					Log.i("total time",
							(System.currentTimeMillis() - total_start) + "[ms]");
				}
			}
		}
	}

	/**
	 * Meataio Callback
	 * 
	 * @author maruyayoshihisa
	 * 
	 */
	byte[] tmp_buffer = null;

	private class UnifeyeCallbackHandler extends IMetaioSDKCallback {
		/**
		 * onNewCameraFrameが呼ばれまくると、OutOfMemoryが起きる。
		 * リクエストが終わったらこのメソッドを呼ぶかに切り替える。
		 */
		@Override
		public void onNewCameraFrame(ImageStruct cameraFrame) {
			String color_format = cameraFrame.getColorFormat().toString();
			RequestSerializer rs = convertDataFormatUsingOpenCV.makeRequest(cameraFrame.getWidth(),
					cameraFrame.getHeight(), cameraFrame.getBuffer(),
					color_format);
			ct.push(rs);
		}
	}

	@Override
	protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
		return mCallbackHandler;
	}

	final class MetaioSDKCallbackHandler extends IMetaioSDKCallback {
		@Override
		public void onSDKReady() {
			// show GUI
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mGUIView.setVisibility(View.VISIBLE);
				}
			});
		}
	}

	@Override
	protected void onGeometryTouched(IGeometry geometry) {
		// TODO Auto-generated method stub

	}

}
