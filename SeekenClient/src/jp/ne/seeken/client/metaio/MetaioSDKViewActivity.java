// Copyright 2007-2012 metaio GmbH. All rights reserved.
package jp.ne.seeken.client.metaio;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.MetaioSurfaceView;
import com.metaio.sdk.SensorsComponentAndroid;
import com.metaio.sdk.jni.ERENDER_SYSTEM;
import com.metaio.sdk.jni.ESCREEN_ROTATION;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IMetaioSDKAndroid;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.MetaioSDK;
import com.metaio.sdk.jni.Vector2di;
import com.metaio.tools.Memory;
import com.metaio.tools.Screen;
import com.metaio.tools.SystemInfo;

/**
 * This is base activity to use metaio SDK. It creates GLSurfaceView and handle
 * all its callbacks and lifecycle.
 * 
 * 
 * @author arsalan.malik
 * 
 */
public abstract class MetaioSDKViewActivity extends Activity implements
		MetaioSurfaceView.Callback, OnTouchListener, Camera.AutoFocusCallback {
	static {
		IMetaioSDKAndroid.loadNativeLibs();
	}

	/**
	 * Sensor manager
	 */
	protected SensorsComponentAndroid mSensors;

	/**
	 * OpenGL View
	 */
	protected MetaioSurfaceView mSurfaceView;

	/**
	 * GUI overlay, only valid in onStart and if a resource is provided in
	 * getGUILayout.
	 */
	protected View mGUIView;

	/**
	 * metaioSDK object
	 */
	protected IMetaioSDKAndroid metaioSDK;

	/**
	 * flag for the renderer
	 */
	protected boolean mRendererInitialized;

	/**
	 * Wake lock to avoid screen time outs.
	 * <p>
	 * The application must request WAKE_LOCK permission.
	 */
	protected PowerManager.WakeLock mWakeLock;

	/**
	 * metaio SDK callback handler
	 */
	private IMetaioSDKCallback mHandler;

	/**
	 * Camera image resolution
	 */
	protected Vector2di mCameraResolution;

	/**
	 * enable/disable see-through mode
	 */
	protected boolean mSeeThrough;

	/**
	 * Provide resource for GUI overlay if required.
	 * <p>
	 * The resource is inflated into mGUIView which is added in onStart
	 * 
	 * @return Resource ID of the GUI view
	 */
	protected abstract int getGUILayout();

	/**
	 * Provide SDK callback handler if desired.
	 * 
	 * @return Return sdk callback handler
	 */
	protected abstract IMetaioSDKCallback getMetaioSDKCallbackHandler();

	/**
	 * Load contents to sdk in this method, e.g. tracking data, geometries etc.
	 */
	protected abstract void loadContent();

	/**
	 * Called when a geometry is touched.
	 * 
	 * @param geometry
	 *            Geometry that is touched
	 */
	protected abstract void onGeometryTouched(IGeometry geometry);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MetaioDebug.log("MetaioSDKViewActivity.onCreate(){");
		metaioSDK = null;
		mSurfaceView = null;
		mRendererInitialized = false;
		mHandler = null;
		mSeeThrough = false;

		try {
			// Create sensors manager
			mSensors = new SensorsComponentAndroid(getApplicationContext());

			// create the SDK instance

			final String signature = "JAj61k/6zSOizUnd9bSy3LpEb7MuWVfRkqQjenTkcdQ=";

			// Create sdk by passing Activity instance and application signature
			metaioSDK = MetaioSDK.CreateMetaioSDKAndroid(this, signature);
			metaioSDK.registerSensorsComponent(mSensors);
			metaioSDK.setSeeThrough(mSeeThrough);

			// Inflate GUI view if provided
			final int layout = getGUILayout();
			if (layout != 0)
				mGUIView = View.inflate(this, layout, null);
		} catch (Exception e) {
			MetaioDebug.log(Log.ERROR, "Error creating metaio SDK");
			finish();
		}

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
				getPackageName());

	}

	@Override
	protected void onStart() {
		super.onStart();

		try {
			mSurfaceView = null;

			if (metaioSDK != null) {
				// Set empty content view
				setContentView(new FrameLayout(this));

				// now activate the back facing camera
				final int cameraIndex = SystemInfo
						.getCameraIndex(CameraInfo.CAMERA_FACING_BACK);
				//mCameraResolution = metaioSDK						.startCamera(cameraIndex, 320, 240);

				// TODO: can also start camera at higher resolution with
				// optional downscaling

			    mCameraResolution = metaioSDK.startCamera(0, 640, 480, 2);

				// Add GL Surface view
				mSurfaceView = new MetaioSurfaceView(this);
				mSurfaceView.registerCallback(this);
				mSurfaceView.setKeepScreenOn(true);
				mSurfaceView.setOnTouchListener(this);

				MetaioDebug
						.log("MetaioSDKViewActivity.onStart: addContentView(mSurfaceView)");
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				addContentView(mSurfaceView, params);
				mSurfaceView.setZOrderMediaOverlay(true);

			}

			// If GUI view is inflated, add it
			if (mGUIView != null) {
				addContentView(mGUIView, new LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				mGUIView.bringToFront();
			}

		} catch (Exception e) {
			MetaioDebug.log(Log.ERROR,
					"Error creating views: " + e.getMessage());
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		MetaioDebug.log("MetaioSDKViewActivity.onPause()");

		if (mWakeLock != null)
			mWakeLock.release();

		// pause the sdk surface
		if (mSurfaceView != null)
			mSurfaceView.onPause();

		if (metaioSDK != null)
			metaioSDK.pause();

	}

	@Override
	protected void onResume() {
		super.onResume();
		MetaioDebug.log("MetaioSDKViewActivity.onResume()");

		if (mWakeLock != null)
			mWakeLock.acquire();

		// make sure to resume the sdk surface
		if (mSurfaceView != null)
			mSurfaceView.onResume();

		if (metaioSDK != null)
			metaioSDK.resume();

	}

	@Override
	protected void onStop() {
		super.onStop();

		MetaioDebug.log("MetaioSDKViewActivity.onStop()");

		if (metaioSDK != null) {
			// Disable the camera
			metaioSDK.stopCamera();
		}

		if (mSurfaceView != null) {
			ViewGroup v = (ViewGroup) findViewById(android.R.id.content);
			v.removeAllViews();
		}

		System.runFinalization();
		System.gc();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		MetaioDebug.log("MetaioSDKViewActivity.onDestroy");

		if (metaioSDK != null) {
			metaioSDK.delete();
			metaioSDK = null;
		}

		if (mSensors != null) {
			mSensors.release();
			mSensors.registerCallback(null);
			mSensors.delete();
			mSensors = null;
		}

		Memory.unbindViews(findViewById(android.R.id.content));

		System.runFinalization();
		System.gc();

	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	/**
	 * Set screen rotation
	 */
	private void setScreenRotation() {
		ESCREEN_ROTATION screenRotation = Screen.getRotation(this);

		MetaioDebug.log(Log.INFO, "Setting screen rotation to "
				+ screenRotation);
		metaioSDK.setScreenRotation(screenRotation);

	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
			MetaioDebug.log("ARViewActivity touched at: " + event.toString());

			try {

				final int x = (int) event.getX();
				final int y = (int) event.getY();

				// ask the SDK if a geometry has been hit
				IGeometry geometry = metaioSDK
						.getGeometryFromScreenCoordinates(x, y, true);
				if (geometry != null) {
					MetaioDebug.log("ARViewActivity geometry found: "
							+ geometry);
					onGeometryTouched(geometry);
				}

			} catch (Exception e) {
				MetaioDebug.log(Log.ERROR, "onTouch: " + e.getMessage());
			}
			
		}
		
		 //1本の指でタッチされたときに実行
        if (event.getAction()==MotionEvent.ACTION_DOWN) {
            //オートフォーカス機能を呼び出す
    		this.mCamera= metaioSDK.getCamera(this);
    		this.mCamera.autoFocus(this);
        }
	

		//foucus

		// don't ask why we always need to return true contrary to what
		// documentation says
		return true;
	}
	
	Camera mCamera;
	@Override //Camera.AutoFocusCallback
	public void onAutoFocus(boolean success, Camera camera) {
		mCamera.autoFocus(null);
	}

	/**
	 * This function will be called, right after the OpenGL context was created.
	 * All calls to SDK must be done after this callback has been triggered.
	 */
	@Override
	public void onSurfaceCreated() {
		MetaioDebug.log("MetaioSDKViewActivity.onSurfaceCreated: GL thread: "
				+ Thread.currentThread().getId());
		try {
			// initialized the renderer
			if (!mRendererInitialized) {
				metaioSDK.initializeRenderer(mSurfaceView.getWidth(),
						mSurfaceView.getHeight(), Screen.getRotation(this),
						ERENDER_SYSTEM.ERENDER_SYSTEM_OPENGL_ES_2_0);
				loadContent();
				mRendererInitialized = true;
			} else {
				MetaioDebug
						.log("MetaioSDKViewActivity.onSurfaceCreated: Reloading textures...");
				metaioSDK.reloadTextures();
			}

			// connect the audio callbacks
			MetaioDebug
					.log("MetaioSDKViewActivity.onSurfaceCreated: Registering audio renderer...");
			metaioSDK.registerAudioCallback(mSurfaceView.getAudioRenderer());
			//mHandler = getMetaioSDKCallbackHandler();
			//if (mHandler != null)
			//	metaioSDK.registerCallback(mHandler);

			MetaioDebug.log("MetaioSDKViewActivity.onSurfaceCreated");
			//this.mCamera = metaioSDK.getCamera(this);
			//setCameraCallback(mCamera);
			
		} catch (Exception e) {
			MetaioDebug
					.log(Log.ERROR, "MetaioSDKViewActivity.onSurfaceCreated: "
							+ e.getMessage());
		}
	}
	
	protected void createMetaioSDK(){
		
	}

	@Override
	public void onDrawFrame() {
		try {
			// render the the results
			if (mRendererInitialized)
				metaioSDK.render();

		} catch (Exception e) {

		}
	}

	@Override
	public void onSurfaceDestroyed() {
		MetaioDebug.log("MetaioSDKViewActivity.onSurfaceDestroyed()");
		mSurfaceView = null;
	}

	@Override
	public void onSurfaceChanged(int width, int height) {
		MetaioDebug.log("MetaioSDKViewActivity.onSurfaceChanged: " + width
				+ ", " + height);

		// correct screen rotation must be set before resizing renderer viewport
		setScreenRotation();

		// resize renderer viewport
		metaioSDK.resizeRenderer(width, height);
	}

}