package com.image.imagecapture;/**
 * Created by Charles Raj I on 06/09/21.
 *
 * @author Charles Raj I
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.common.util.concurrent.ListenableFuture;
import com.image.imagecapture.databinding.CameraActivityBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment implements CameraListener {

    private static final String TAG = "CameraActivity";
    public static final String internalStorageDir = "/images";
    public static final String videoFormat = ".mp4";
    public static final String imageFormat = ".jpg";
    public static final String[] CAMERA_PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO};
    CameraActivityBinding cameraActivityBinding;
    Activity activity;
    ExecutorService camaraExecutor;
    CameraSelector cameraSelector;
    ImageCapture imageCapture;
    ProcessCameraProvider cameraProvider;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;


    private int REQUEST_CODE_PERMISSIONS = 1001;
    VideoCapture videoCapture;
    String imagePath= "";
    CameraFragmentToActivity cameraFragmentToActivity;
    int lenceFacing;

    Boolean videoStatus= false;
    Camera camera;
    int miliSec = 0;
    int sec = 0;
    boolean isFlashOn = false;

    private boolean started = false;
    private Handler handler = new Handler();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        cameraActivityBinding = DataBindingUtil.inflate(LayoutInflater.from(activity), R.layout.camera_activity,container,false);
        cameraActivityBinding.setCameraListener(this);
        camaraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraActivityBinding.flashLight.setEnabled(false);

        if (allPermissionsGranted(CAMERA_PERMISSIONS,activity)) {
            startCamera();
        }else {
            activity.requestPermissions(CAMERA_PERMISSIONS,1);
        }

        cameraActivityBinding.getRoot().setFocusableInTouchMode(true);
        cameraActivityBinding.getRoot().requestFocus();
        cameraActivityBinding.getRoot().setOnKeyListener((v, keyCode, event) -> {
            if( keyCode == KeyEvent.KEYCODE_BACK )
            {
                cameraFragmentToActivity.fragmentBackPress();
                return true;
            }
            return false;
        });

        cameraActivityBinding.cambackPress.setOnClickListener(v -> {
            cameraFragmentToActivity.fragmentBackPress();
        });
        return cameraActivityBinding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        cameraFragmentToActivity = (CameraFragmentToActivity) context;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if (allPermissionsGranted(permissions,activity)) {
                startCamera();
            } else{
                activity.requestPermissions(permissions,1);
                Toast.makeText(activity, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static  boolean allPermissionsGranted(String[] permissions, Activity activity){

        for(String permission : permissions){
            if(ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    lenceFacing = CameraSelector.LENS_FACING_BACK;
                    initPreview(cameraProvider,lenceFacing);

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    @SuppressLint("RestrictedApi")
    void initPreview(@NonNull ProcessCameraProvider cameraProvider, int lensFacingBack) {
            cameraProvider.unbindAll();

            try {

                // preview user case
                Preview preview = new Preview.Builder()
                        .build();

                preview.setSurfaceProvider(cameraActivityBinding.viewFinder.getSurfaceProvider());

                // camera selector use case
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacingBack)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // imagecapture user case
                ImageCapture.Builder builder = new ImageCapture.Builder();
                imageCapture = builder
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(activity.getWindowManager().getDefaultDisplay().getRotation())
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();

                // video capture use case
                videoCapture = new VideoCapture.Builder()
                        .setTargetRotation(activity.getWindowManager().getDefaultDisplay().getRotation())
                        .setCameraSelector(cameraSelector)
//                        .setVideoFrameRate(30)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();


                preview.setSurfaceProvider(cameraActivityBinding.viewFinder.getSurfaceProvider());
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture,videoCapture);


            }catch (Exception e){
                e.printStackTrace();
                Log.d(TAG, "bindPreview: Exception" + e.getMessage());
            }
        }


    @Override @SuppressLint("RestrictedApi")
    public void takePicture(View view) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (!videoStatus) {
                Log.d(TAG, "takePicture: click ");
                imagePath = "";
                try {
                    imageCapture.takePicture(camaraExecutor, new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            Log.d("somthing", "onCaptureSuccess: success  ---");

                            showImage(activity, imageProxyToBitmap(image), cameraActivityBinding.previewImage);
                            activity.runOnUiThread(() -> {
                                cameraActivityBinding.cameraLayout.setVisibility(View.GONE);
                                cameraActivityBinding.previewLayout.setVisibility(View.VISIBLE);
                                cameraActivityBinding.previewImage.setVisibility(View.VISIBLE);
                                cameraActivityBinding.previewVideo.setVisibility(View.GONE);
                            });
                            Log.d(TAG, "Image saved path: " + imagePath);
                            imagePath = saveTOInternamMemory(activity, imageProxyToBitmap(image));
                            activity.runOnUiThread(() -> {
                                cameraActivityBinding.submitImg.setVisibility(View.VISIBLE);
                            });
                            image.close();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            exception.printStackTrace();
                            Toast.makeText(activity, exception.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.d("somthing", "onCaptureSuccess: " + exception.getMessage());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.d("somthing", "try catch: " + e.getMessage());
                }

            }else {
               stopRecording();
            }
        }
    }


    public void showImage(Activity activity, Bitmap value, ImageView imageView){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Glide.with(activity)
                        .asBitmap()
                        .load(value)
                        .into(new BitmapImageViewTarget(imageView) {
                            @Override
                            protected void setResource(Bitmap resource) {
                                //Play with bitmap
                                super.setResource(resource);
                            }
                        });

            }
        });
    }

    // output of the image capture image proxy to bitmap
    public Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        buffer.rewind();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        byte[] clonedBytes = bytes.clone();
        return BitmapFactory.decodeByteArray(clonedBytes, 0, clonedBytes.length);
    }

    // used for save the files internal storage , can view in the gallery or internal storage
    public String saveTOInternamMemory(Activity activity, Bitmap bitmapImage){

        File myPath = getInternalStorageDir(internalStorageDir,imageFormat,Environment.DIRECTORY_PICTURES);

        Log.d(TAG, "directory: " + myPath.getAbsolutePath());

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(myPath);
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.d(TAG, "bit exception: Success" );
        } catch (Exception e) {
            Log.d(TAG, "bit exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "io exce: " + e.getMessage());
            }
        }
        Log.d(TAG, "absolute path " + myPath.getAbsolutePath());
        return myPath.getAbsolutePath();
    }

    public File getInternalStorageDir(String internalStorageDir, String fileType, String dirType){
        Date date = new Date();
        String milisec = String.valueOf(date.getTime());
        File photosDir = new File(Environment.getExternalStoragePublicDirectory(dirType) + internalStorageDir);
        // Create imageDir
        if (!photosDir.exists()) photosDir.mkdir();
        return new File(photosDir, milisec + fileType);
    }
    @Override
    public void takeVideo(View view) {

    }

    @Override
    public void submitClick(View view) {
        if (!TextUtils.isEmpty(imagePath))
            cameraFragmentToActivity.sendUrl(imagePath);
    }

    @Override
    public void changeCamera(View view) {
        cameraProvider.unbindAll();
        if(lenceFacing == CameraSelector.LENS_FACING_FRONT) {
            initPreview(cameraProvider, CameraSelector.LENS_FACING_BACK);
            lenceFacing = CameraSelector.LENS_FACING_BACK;
        }else {
            initPreview(cameraProvider, CameraSelector.LENS_FACING_FRONT);
            lenceFacing = CameraSelector.LENS_FACING_FRONT;
        }
    }

    @Override
    public void closeBackToCam(View view) {
        activity.runOnUiThread(() -> {
            imagePath ="";
            cameraActivityBinding.cameraLayout.setVisibility(View.VISIBLE);
            cameraActivityBinding.previewLayout.setVisibility(View.GONE);
            cameraActivityBinding.previewVideo.stopPlayback();

            cameraActivityBinding.miliSecondsRec.setText(String.valueOf(0));
            cameraActivityBinding.secondsRec.setText(String.valueOf(0));
            cameraActivityBinding.countDownLay.setVisibility(View.GONE);
        });

        cameraProvider.unbindAll();
        initPreview(cameraProvider, CameraSelector.LENS_FACING_BACK);
        lenceFacing = CameraSelector.LENS_FACING_BACK;
    }

    @Override
    public void flashOnOff(View view, ImageView imageView) {
        try {
            if (isFlashOn){
                camera.getCameraControl().enableTorch(false);
                isFlashOn = false;
                imageView.setEnabled(false);
            }else {
                camera.getCameraControl().enableTorch(true);
                isFlashOn = true;
                imageView.setEnabled(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "onCreateView: set tourch error" + e.getMessage());
        }

    }

    @SuppressLint("RestrictedApi")
    public void stopRecording(){
        videoCapture.stopRecording();
        videoStatus = false;
        cameraActivityBinding.cameraCaptureButton.setActivated(false);
        stopCount();
    }


    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
                miliSec++;
                Log.d(TAG, "recordingCount: " + miliSec);
                cameraActivityBinding.miliSecondsRec.setText(String.valueOf(miliSec));
                if (miliSec == 60) {
                    sec++;
                    cameraActivityBinding.secondsRec.setText(String.valueOf(sec));
                    miliSec = 0;
                }
            if (sec == 2){
                stopCount();
            }
            if(started) {
                startCount();
            }
        }
    };

    public void stopCount() {
        started = false;
        miliSec = 0;
        sec = 0;
        handler.removeCallbacks(runnable);
    }

    public void startCount() {
        cameraActivityBinding.countDownLay.setVisibility(View.VISIBLE);
        started = true;
        handler.postDelayed(runnable, 1000);
    }

}