#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/highgui/highgui.hpp>
//#include <opencv2/legacy/legacy.hpp> for OpenCV 2.4.x
#include <vector>
#include <android/log.h>

#define LOG_TAG "JNI_PART"
#define LOGV(...) __android_log_print(ANDROID_LOG_SILENT, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

extern "C" {
    
    /**
     * 
     */
    JNIEXPORT void JNICALL Java_jp_ne_seeken_client_ConvertDataFormatUsingOpenCV_getGrayByteArray(JNIEnv* env, jobject thiz, jint width, jint height, jbyteArray argb,jbyteArray gray) {
        
        
        //java配列からc++の配列へ
        jbyte* _argb = env->GetByteArrayElements(argb, 0);
        jbyte* _gray = env->GetByteArrayElements(gray, 0);
        
        Mat mrgb(height, width, CV_8UC4, (unsigned char *) _argb);
        
        //COLOR_mRGBA2RGBA
        Mat mdst(height,width,CV_8UC4);
        cvtColor(mrgb, mdst, CV_BGRA2RGBA);
        
        //グレーイメジに変換
        Mat mgray(height, width, CV_8UC1);
        cvtColor(mdst, mgray, CV_RGBA2GRAY, 0);
        normalize(mgray, mgray, 0, 255, NORM_MINMAX);
        
        //必要??
        for(int x = 0; x < height; x++){
            int c = x * width;
            for(int y = 0; y < width; y++){
                _gray[c + y] = (unsigned char)mgray.data[c + y];
            }
        }
        
        //リソースのリリースとjava配列へのコピー
        env->ReleaseByteArrayElements(argb,_argb,0);
        env->ReleaseByteArrayElements(gray,_gray,0);
    }
    
    
    /**
    *
    */
    JNIEXPORT void JNICALL Java_jp_ne_seeken_client_ConvertDataFormatUsingOpenCV_saveGrayByteArray(JNIEnv* env, jobject thiz, jint width, jint height, jbyteArray gray,jstring save_path) {
        
        //セーブパスを取得
        const char* _save_path = env->GetStringUTFChars(save_path, NULL);
        LOGE(_save_path);
        
        //java配列からc++の配列へ
        jbyte* _gray = env->GetByteArrayElements(gray, 0);
        
        Mat mgray(height, width, CV_8UC1, (unsigned char *) _gray);
    
        imwrite(_save_path,mgray);
        
        //リソースのリリースとjava配列へのコピー
        env->ReleaseStringUTFChars(save_path, _save_path);
        env->ReleaseByteArrayElements(gray,_gray,0);
    }
    
}

