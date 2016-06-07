package com.teaminfernale.gazetracker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.flandmark;
import org.bytedeco.javacv.CanvasFrame;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.bytedeco.javacpp.flandmark.*;
import static org.bytedeco.javacpp.flandmark.*;
import org.bytedeco.javacpp.opencv_core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


public abstract class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final String TAG1 = "MainActivity_calibr";
    public static final int JAVA_DETECTOR = 0;
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;

    //    protected TrainedEyesContainer mTrainedEyesContainer = new TrainedEyesContainer();
//    private  GazeCalculator mGazeCalculator;
//    private boolean calibrating = false;
//    private int calibration_phase = 0;
    private int learn_frames = 0;
    private Mat teplateR;
    private Mat teplateL;
    int method = 0;

    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private MenuItem mItemType;

    private Mat mRgba;
    private Mat mGray;
    // matrix for zooming
    private Mat mZoomWindow;
    private Mat mZoomWindow2;

    private CascadeClassifier mJavaDetector;
    private CascadeClassifier mJavaDetectorEye;
    private CascadeClassifier mJavaDetectorFace;
    private FLANDMARK_Model model;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private boolean calibrated = false;
    private boolean monitoring = false;

    private boolean wantToSave = false;

    private int mode = 0;

    Point R_upRight, L_upRight, R_upLeft, L_upLeft, R_downRight, L_downRight, R_downLeft, L_downLeft = new Point();
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient mClient;

    protected abstract void onEyeFound(Point leftEye, Point rightEye, Bitmap le, Bitmap re);

    public void setModeRecognition() {
        mode = 1;
    }

    enum landmark_pos {
        FACE_CENTER(0),
        LEFT_EYE_INNER(1) ,
        RIGHT_EYE_INNER(2),
        MOUTH_LEFT(3),
        MOUTH_RIGHT(4),
        LEFT_EYE_OUTER(5),
        RIGHT_EYE_OUTER(6),
        NOSE_CENTER(7),
        LEFT_EYE_ALIGN(8),
        RIGHT_EYE_ALIGN(9);

        int value;

        private landmark_pos(int value) {
            this.value = value;
        }



    };

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");


                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(cascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        // Load left eye classificator
                        InputStream iser = getResources().openRawResource(
                                R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirER = getDir("cascadeER",
                                Context.MODE_PRIVATE);
                        File cascadeFileER = new File(cascadeDirER,
                                "haarcascade_eye_right.xml");
                        FileOutputStream oser = new FileOutputStream(cascadeFileER);

                        byte[] bufferER = new byte[4096];
                        int bytesReadER;
                        while ((bytesReadER = iser.read(bufferER)) != -1) {
                            oser.write(bufferER, 0, bytesReadER);
                        }
                        iser.close();
                        oser.close();


                        // Load face cascade classificator
                        InputStream isfc = getResources().openRawResource(
                                R.raw.haarcascade_frontalface_alt);
                        File cascadeDirFC = getDir("cascadeFC",
                                Context.MODE_PRIVATE);
                        File cascadeFileFC = new File(cascadeDirFC,
                                "haarcascade_frontalface_alt.xml");
                        FileOutputStream osfc = new FileOutputStream(cascadeFileFC);

                        byte[] bufferFC = new byte[4096];
                        int bytesReadFC;
                        while ((bytesReadFC = isfc.read(bufferFC)) != -1) {
                            osfc.write(bufferFC, 0, bytesReadFC);
                        }
                        isfc.close();
                        osfc.close();


                        // Load flandmark model
                        InputStream isflm = getResources().openRawResource(
                                R.raw.flandmark_model);
                        File cascadeDirFLM = getDir("cascadeFLM",
                                Context.MODE_PRIVATE);
                        File cascadeFileFLM = new File(cascadeDirFLM,
                                "flandmark_model.dat");
                        FileOutputStream osflm = new FileOutputStream(cascadeFileFLM);

                        byte[] bufferFLM = new byte[4096];
                        int bytesReadFLM;
                        while ((bytesReadFLM = isflm.read(bufferFLM)) != -1) {
                            osflm.write(bufferFLM, 0, bytesReadFLM);
                        }
                        isflm.close();
                        osflm.close();


                        mJavaDetector = new CascadeClassifier(
                                cascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from "
                                    + cascadeFile.getAbsolutePath());

                        mJavaDetectorEye = new CascadeClassifier(
                                cascadeFileER.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier - eye");
                            mJavaDetectorEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier (eye) from " + cascadeFileER.getAbsolutePath());

                        cascadeDirER.delete();

                        mJavaDetectorFace = new CascadeClassifier(
                                cascadeFileFC.getAbsolutePath());
                        if (mJavaDetectorFace.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier - face");
                            mJavaDetectorFace = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier (face) from " + cascadeFileFC.getAbsolutePath());

                        cascadeDirFC.delete();


                        Log.i(TAG, "Trying to load flandmark model from " + cascadeFileFLM.getAbsolutePath());
                        model = flandmark_init(cascadeFileFLM.getAbsolutePath());
                        Log.i(TAG, "Loaded flandmark model from " + cascadeFileFLM.getAbsolutePath());
                        cascadeDirFLM.delete();
                        try{
                            //model = flandmark_init(getfileFromResources(R.raw.flandmark_model, "flandmark_model.dat").getAbsolutePath());
                        } catch (Exception e) {
                            Log.i(TAG, "Failed to load flandmark model from " + cascadeFileFLM.getAbsolutePath());
                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.setCameraIndex(1);
                    mOpenCvCameraView.enableView();

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public MainActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    //Serve affinchè venga settato il layout con la fd_activity_surface_view per poter lanciare la fotocamera
    //comando setContentView(R.layout.XXX_activity_layout);
    protected abstract void setLayout();

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Log.i(TAG, "On create called");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setLayout();

//        calibrating = true;

        Log.i(TAG, "initializating camera view");
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        switch (mode) {
            case 0:
                mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
                break;
            default:
                mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.recognition_front_camera_view);
        }

        if (mOpenCvCameraView == null)
            Log.i(TAG, "Capito er bug");
        mOpenCvCameraView.setCvCameraViewListener(this);
        Log.i(TAG, "camera view cameraview initializated");


        //setLayout();
        /*SeekBar methodSeekbar = (SeekBar) findViewById(R.id.methodSeekBar);

        methodSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}});*/
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mClient = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    public void closeCamera() {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        Log.i(TAG, "Camera closed");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        Log.i(TAG, "On pause called");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
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
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mZoomWindow.release();
        mZoomWindow2.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        if (mZoomWindow == null || mZoomWindow2 == null) {
            CreateAuxiliaryMats();
        }

        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null) {
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }

        Point lMatchedEye = new Point();
        Point rMatchedEye = new Point();

        Rect[] facesArray = faces.toArray();
        //Log.i(TAG, "FacesArray length = " + facesArray.length);
        if (facesArray.length > 0) {

            Rect r = facesArray[0];
            // Compute both eyes area
            // Rect eyearea = new Rect(r.x + r.width / 8, (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8, (int) (r.height / 3.0));
            // split it

            // TODO: capire perché vengono usati questi numeri e salvarli in variabili (per capire meglio il codice)
            Rect eyearea_right = new Rect(r.x + r.width / 16, (int) (r.y + (r.height / 4.5)), (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            Rect eyearea_left = new Rect(r.x + r.width / 16 + (r.width - 2 * r.width / 16) / 2, (int) (r.y + (r.height / 4.5)), (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));

            if (learn_frames < 5) {
                teplateR = get_template(mJavaDetectorEye, eyearea_right, 24);
                teplateL = get_template(mJavaDetectorEye, eyearea_left, 24);
                learn_frames++;
            } else {
                // Learning finished, use the new templates for template matching
                lMatchedEye = match_eye(eyearea_left, teplateL, method, mJavaDetectorEye, 0);
                rMatchedEye = match_eye(eyearea_right, teplateR, method, mJavaDetectorEye, 1);
            }

            // Cut eye areas and put them to zoom windows
            Imgproc.resize(mRgba.submat(eyearea_right), mZoomWindow, mZoomWindow.size());
            Imgproc.resize(mRgba.submat(eyearea_left), mZoomWindow2, mZoomWindow2.size());
        }

        final int[] bbox = new int[4];
        final double[] landmarks = new double[2 * model.data().options().M()];

        //detectLandmarks(model, mRgba, , r);

        // On a separate thread it converts the eye mat into a bitmap
        Handler mainHandler = new Handler(getApplicationContext().getMainLooper());

        final Point finalLMatchedEye = lMatchedEye;
        final Point finalRMatchedEye = rMatchedEye;

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {

                try {
                    Bitmap le = Bitmap.createBitmap(mZoomWindow.cols(), mZoomWindow.rows(), Bitmap.Config.ARGB_8888);
                    Bitmap re = Bitmap.createBitmap(mZoomWindow.cols(), mZoomWindow.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(mZoomWindow.clone(), le);
                    Utils.matToBitmap(mZoomWindow2.clone(), re);
                    if (finalLMatchedEye != null && finalRMatchedEye != null)
                        onEyeFound(finalLMatchedEye, finalRMatchedEye, le, re);
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, "EXCEPTION");
                }


            }
        };

        mainHandler.post(myRunnable);

        return mRgba;
    }

    // Called after model training is completed
    private Point match_eye(Rect area, Mat mTemplate, int type, CascadeClassifier clasificator, int eye) {
        //Point matchLoc;
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;

        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return null;
        }

        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        switch (type) {
            case TM_SQDIFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF);
                break;
            case TM_SQDIFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF_NORMED);
                break;
            case TM_CCOEFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF);
                break;
            case TM_CCOEFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF_NORMED);
                break;
            case TM_CCORR:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR);
                break;
            case TM_CCORR_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR_NORMED);
                break;
        }


        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2, Objdetect.CASCADE_FIND_BIGGEST_OBJECT | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30), new Size());

        // It is > 0 if it detects an eye (I suppose). Recorded value: 0 or 1
        Rect[] eyesArray = eyes.toArray();

        if (eyesArray.length > 0) {
            Rect e = eyesArray[0];
            e.x += area.x;
            e.y += area.y;

            Rect eye_only_rectangle = new Rect((int) e.tl().x, (int) (e.tl().y + e.height * 0.4), e.width, (int) (e.height * 0.6));
            Mat mROI2 = mGray.submat(eye_only_rectangle);
            Mat yyrez = mRgba.submat(eye_only_rectangle);

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            Imgproc.circle(yyrez, mmG.minLoc, 1, new Scalar(255, 255, 255, 255), 1);
            //Log.i(TAG, (eye == 0) ? "Left" : "Right" + " eye detected\t Center = ( " + mmG.minLoc.x + ", " + mmG.minLoc.y + " )");

            //DA SPOSTARE IN CALIBRATION!!!!
//            if (calibrating) {
//                mTrainedEyesContainer.addSample(eye, calibration_phase, mmG.minLoc);
//            }

            return mmG.minLoc;
            // Prendere un punto di riferimento del rettangolo e tracciare i movimenti dell'iride rispetto a quel punto
            // Tracciare i vari punti sullo schermo
        }

        return null;
    }

    // First 6 frames to train the model
    private Mat get_template(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template;
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2, Objdetect.CASCADE_FIND_BIGGEST_OBJECT | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30), new Size());

        Rect[] eyesArray = eyes.toArray();
        if (eyesArray.length > 0) {
            Rect e = eyesArray[0];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x, (int) (e.tl().y + e.height * 0.4), (int) e.width, (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            // Draws a point in the center of the eye
            //Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y - size / 2, size, size);

            // Draws a red rectangle around the center of the eye
            // Imgproc.rectangle(mRgba, eye_template.tl(), eye_template.br(), new Scalar(255, 0, 0, 255), 2);
            template = (mGray.submat(eye_template)).clone();

            return template;
        }

        return template;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2 + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2 + cols / 10, cols);
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        mItemType = menu.add(mDetectorName[mDetectorType]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        else if (item == mItemType) {
            int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
            item.setTitle(mDetectorName[tmpDetectorType]);
        }
        return true;
    }

    public void onRecreateClick(View v) {
        learn_frames = 0;
    }

    /** detects landmarks using flandmakrs and add two more landmakrs to be used to alignt the face*/
   /* private static ArrayList<Point> detectLandmarks(FLANDMARK_Model model, final Mat  image,final opencv_core.IplImage gray_image_iipl, final Rect  face){

        ArrayList<Point> landmarks = new ArrayList<Point>();

        // 1. get landmarks (using flandmarks)
        int bbox [] = { face.x, face.y, face.x + face.width, face.y + face.height };
        double [] points = new double [2 * model.data().options().M()];

        if( flandmark_detect(gray_image_iipl, bbox, model,points) > 0){
            return landmarks;
        }

        for (int i = 0; i < model.data().options().M(); i++) {
            landmarks.add(new Point(points[2 * i], points[2 * i + 1]));
        }

        // 2. get a linar regresion using the four points of the eyes
        LinearRegression lr = new LinearRegression();
        lr.addPoint(landmarks.get(landmark_pos.LEFT_EYE_OUTER.value).x,landmarks.get(landmark_pos.LEFT_EYE_OUTER.value).y);
        lr.addPoint(landmarks.get(landmark_pos.LEFT_EYE_INNER.value).x,landmarks.get(landmark_pos.LEFT_EYE_INNER.value).y);
        lr.addPoint(landmarks.get(landmark_pos.RIGHT_EYE_INNER.value).x,landmarks.get(landmark_pos.RIGHT_EYE_INNER.value).y);
        lr.addPoint(landmarks.get(landmark_pos.RIGHT_EYE_OUTER.value).x,landmarks.get(landmark_pos.RIGHT_EYE_OUTER.value).y);

        double coef_determination = lr.getCoefDeterm();
        double coef_correlation = lr.getCoefCorrel();
        double standar_error_estimate = lr.getStdErrorEst();

        double a = lr.getA();
        double b = lr.getB();

        // 3. get two more landmarks based on this linear regresion to be used to align the face
        Point pp1 = new Point(landmarks.get(landmark_pos.LEFT_EYE_OUTER.value).x,
                landmarks.get(landmark_pos.LEFT_EYE_OUTER.value).x*b+a);
        Point pp2 = new Point(landmarks.get(landmark_pos.RIGHT_EYE_OUTER.value).x,
                landmarks.get(landmark_pos.RIGHT_EYE_OUTER.value).x*b+a);

        landmarks.add(pp1);
        landmarks.add(pp2);

	       //delete[] points;
	       //points = 0;
        return landmarks;
    }*/


    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mClient.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.teaminfernale.gazetracker/http/host/path")
        );
        AppIndex.AppIndexApi.start(mClient, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.teaminfernale.gazetracker/http/host/path")
        );
        AppIndex.AppIndexApi.end(mClient, viewAction);
        mClient.disconnect();
    }

    public File getfileFromResources(int resID,String filename) throws Exception
    {
        InputStream is = getResources().openRawResource(resID);
        File fileDir = getDir("cascade", Context.MODE_PRIVATE);
        File filetoreturn = new File(fileDir,filename);
        FileOutputStream os = new FileOutputStream(filetoreturn);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        is.close();
        os.close();

        return filetoreturn;
    }
}
