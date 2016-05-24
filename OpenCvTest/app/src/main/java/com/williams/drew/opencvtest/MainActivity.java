package com.williams.drew.opencvtest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

// OpenCV Classes

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {

    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgb;
    Mat mResize;
    Mat mHsv;
    Mat mRgbaT;
    Mat mRgbaF;
    Mat mThresh;
    Mat hierarchy;
    Size sResize;
    Size sOriginal;
    Scalar lowerBounds;
    Scalar upperBounds;
    Scalar RED;
    Point centerPixel;
    Point targetTextX;
    Point targetTextY;
    Point targetCenter;

    int RECTANCLE_AREA_SIZE = 100;
    double SOLIDITY_MIN = 0.04;
    double SOLIDITY_MAX = 0.4;
    int ASPECT_RATIO = 1;
    double CAMERA_FOV = 47;
    double PI = 3.1415926535897;

    double centerX, centerY;
    double angleToTarget;

    ArrayList<MatOfPoint> contours;
    ArrayList<MatOfPoint> selected;
    MatOfInt hull;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.show_camera);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(width, height, CvType.CV_8UC4);
        mRgb = new Mat(width, height, CvType.CV_8UC3);
        mHsv = new Mat(width, height, CvType.CV_8UC3);
        mResize = new Mat(320, 320, CvType.CV_8UC4);
        mThresh = new Mat(320, 320, CvType.CV_8UC2);
        mRgbaT = new Mat(width, height, CvType.CV_8UC4);
        mRgbaF = new Mat(width, height, CvType.CV_8UC4);
        hull = new MatOfInt();
        hierarchy = new Mat(width, height, CvType.CV_8UC3);
        lowerBounds = new Scalar(0, 0, 0);
        upperBounds = new Scalar(20, 255, 255);
        RED = new Scalar(255, 0, 0);
        sResize = new Size(320, 320);
        sOriginal = new Size(960, 960);
        centerPixel = new Point(sResize.width / 2 - .5, sResize.height / 2 - .5);
        contours = new ArrayList<MatOfPoint>();
        selected = new ArrayList<MatOfPoint>();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        contours.clear();
        selected.clear();

        mRgba = inputFrame.rgba();
        Imgproc.resize(mRgba, mResize, sResize);
        Imgproc.cvtColor(mResize, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);
        Core.inRange(mHsv, lowerBounds, upperBounds, mThresh);
        Imgproc.findContours(mThresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        filterContours();
        drawTarget();

        if(selected.size() == 1) {
            Rect targetRectangle = Imgproc.boundingRect(selected.get(0));
            centerX = targetRectangle.br().x - targetRectangle.width / 2;
            centerY = targetRectangle.br().y - targetRectangle.height / 2;
            targetCenter = new Point(centerX, centerY);
            Imgproc.line(mResize, centerPixel, targetCenter, RED);
            Imgproc.circle(mResize, targetCenter, 3, RED);
            DrawCoords(targetRectangle);
            angleToTarget = CalculateAngleBetweenCameraAndPixel();
        }
        Imgproc.resize(mResize, mRgba, sOriginal);
        Core.transpose(mRgba, mRgbaT);
        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
        Core.flip(mRgbaF, mRgba, 1 );
        return mRgba; // This function must return
    }

    private void filterContours() {
        for (int i = 0; i < contours.size(); i++) {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            float aspect = (float)rect.width / (float)rect.height;

            //does solidity calculations
            //Imgproc.convexHull(contours.get(i), hull);
            //double area = Imgproc.contourArea(contours.get(i));


            //double hull_area = Imgproc.contourArea(hull);
            //double solidity = (float)area / hull_area;

            if(aspect > 1 && rect.area() > 400) {
                selected.add(contours.get(i));
            }
            /*if (aspect > ASPECT_RATIO && rect.area() > RECTANCLE_AREA_SIZE && (solidity >= SOLIDITY_MIN && solidity <= SOLIDITY_MAX)) {

            }*/
        }
    }

    private void drawTarget() {
        for (int i = 0; i < selected.size(); i++) {
            Rect rect = Imgproc.boundingRect(selected.get(i));
            Imgproc.rectangle(mResize, rect.br(), rect.tl(), RED);
        }
    }

    private void DrawCoords(Rect targetBoundingRect) {
        Rect rect = targetBoundingRect;
        targetTextX = new Point(rect.br().x - rect.width / 2 - 15, rect.br().y - rect.height / 2 - 20);
        targetTextY = new Point(rect.br().x - rect.width / 2 - 15, rect.br().y - rect.height / 2);
        Imgproc.putText(mResize, Double.toString(centerX), targetTextX, Core.FONT_HERSHEY_PLAIN, 1, RED);
        Imgproc.putText(mResize, Double.toString(centerY), targetTextY, Core.FONT_HERSHEY_PLAIN, 1, RED);
    }

    private double CalculateAngleBetweenCameraAndPixel() {
        double focalLengthPixels = .5 * sResize.width / Math.tan((CAMERA_FOV * (PI / 180)) / 2);
        double angle = Math.atan((targetCenter.x - centerPixel.x) / focalLengthPixels);
        double angleDegrees = angle * (180 / PI);
        return angleDegrees;
    }
}