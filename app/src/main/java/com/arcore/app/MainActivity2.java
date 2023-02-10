package com.arcore.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity2 extends AppCompatActivity implements Scene.OnUpdateListener {
    private ArFragment arFragment;

    private String filePointsCloud = "";
    private String filePointsLog = "";
    private String fileVideo = "";
    private Button scanButton;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private LocalDateTime now = LocalDateTime.now();

    private FileOutputStream outputStream = null;
    private FileOutputStream logOutputStream = null;

    private StringBuilder vertexData = new StringBuilder();
    private StringBuilder logData = new StringBuilder();

    private boolean addHeader = true;
    private int totalPointsCount = 0;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize variables
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);


        // Set up a button click listener to start the scan
        scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> {
            //  String date= new SimpleDateFormat("MM-dd hh:mm a").format(new java.util.Date());
            Log.d("total points", "total" + totalPointsCount);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    filePointsCloud = "CloudPoints" + System.currentTimeMillis() + ".ply";
                    filePointsLog = "CloudPoints" + System.currentTimeMillis() + ".txt";
                    fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";
                    toggleRecording();
                } else {
                    ActivityCompat.requestPermissions(MainActivity2.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 40);
                }
            }
        });

        if (arFragment.getArSceneView() != null) {
            arFragment.getArSceneView().getScene().addOnUpdateListener(this);
        }

        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (hitResult.getTrackable().getTrackingState() == TrackingState.TRACKING) {
                scanButton.setVisibility(View.VISIBLE);
            } else {
                scanButton.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        Collection<Plane> plane = arFragment.getArSceneView().getArFrame().getUpdatedTrackables(Plane.class);

        for (Plane p : plane) {
            if (p.getTrackingState() == TrackingState.TRACKING) {
                scanButton.setVisibility(View.VISIBLE);
            }
        }

        if (isRecording) {
            onUpdateFrame();
        }
    }


    private void onUpdateFrame() {
        totalPointsCount = totalPointsCount + 1;
        Frame frame = arFragment.getArSceneView().getArFrame();
        long frameCameraTimeStamp = frame.getAndroidCameraTimestamp();
        LightEstimate frameLightEstimate = frame.getLightEstimate();
        Pose frameAndroidSensorPose = frame.getAndroidSensorPose();
        long frameTimeStamp = frame.acquirePointCloud().getTimestamp();
        IntBuffer frameIds = frame.acquirePointCloud().getIds();
        FloatBuffer pointCloudData = frame.acquirePointCloud().getPoints();
        ArSceneView arSceneView = arFragment.getArSceneView();
        int width = arSceneView.getWidth();
        int height = arSceneView.getHeight();

        // Create a Bitmap from the ArSceneView
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        arSceneView.draw(canvas);
        String strHeader = "ply\n" +
                "format ascii 1.0\n" +
                "comment Point Cloud for 3D Reconstruction\n" +
                "element vertex 25294.0\n" +
                "property float x\n" +
                "property float y\n" +
                "property float z\n" +
                "property uint8 red\n" +
                "property uint8 green\n" +
                "property uint8 blue\n" +
                "end_header\n";

        // Get the RGB values for each pixel in the Bitmap
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePointsCloud);
        File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePointsLog);
        try {
            outputStream = new FileOutputStream(file, true);
            logOutputStream = new FileOutputStream(logFile, true);
            if (addHeader) {
                outputStream.write(strHeader.getBytes());
                addHeader = false;
                totalPointsCount = 0;
            }

            for (int i = 0; i < pointCloudData.capacity() / 3; i++) {
                float x = pointCloudData.get(3 * i);
                float y = pointCloudData.get(3 * i + 1);
                float z = pointCloudData.get(3 * i + 2);
                // Assign some arbitrary RGB values to each vertex (you can modify this to get the actual RGB values from the image)
                int red = (int) (Math.random() * 256);
                int green = (int) (Math.random() * 256);
                int blue = (int) (Math.random() * 256);
                totalPointsCount++;
                vertexData.append(x).append(" ").append(y).append(" ").append(z).append(" ").append(red).append(" ").append(green).append(" ").append(blue).append("\n");
                logData.append(" (").append(totalPointsCount).append(") ").append(x).append(" ").append(y).append(" ").append(z).append(" ").append(red).append(" ").append(green).append(" ").append(blue).append(" ").append(" Frame Time:").append(frameTimeStamp).append(" Frame Camera Time Stamp:").append(frameCameraTimeStamp).append(" Frame Light Estimate:").append(frameLightEstimate).append(" Frame Sensor Pose:").append(frameAndroidSensorPose).append(" Frame ID:").append(frameIds.toString()).append(" Time:").append(formatter.format(now)).append("\n");
                Log.d("outputStream", vertexData.toString());
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
            logData.append("FileNotFoundException").append(e.getMessage()).append("\n");

        } catch (IOException e) {
            e.printStackTrace();
            logData.append("IOException").append(e.getMessage()).append("\n");
        }

    }


    private void toggleRecording() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileVideo);

        if (isRecording) {
            // Stop recording
            isRecording = false;
            scanButton.setText("Record");
            try {
//                float[] pose = arFragment.getArSceneView().getSession().get.getPose().getTranslation();
//                float x = pose[0];
//                float y = pose[1];
//                float z = pose[2];
                vertexData.append("\n" + totalPointsCount + "");
                outputStream.write(vertexData.toString().getBytes());
                outputStream.close();
                logOutputStream.write(logData.toString().getBytes());
                logOutputStream.close();
                arFragment.getArSceneView().getSession().stopRecording();

            } catch (RecordingFailedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();

            }
            // You can also show a toast message or update a UI element to indicate that the recording has stopped
        } else {
            // Start recording
            scanButton.setText("Stop");
            isRecording = true;
            String filename = generateFilename();
            RecordingConfig recordingConfig = new RecordingConfig(arFragment.getArSceneView().getSession());
            recordingConfig.setRecordingRotation(90);

            recordingConfig.setMp4DatasetUri(Uri.fromFile(file));

            try {
                arFragment.getArSceneView().getSession().startRecording(recordingConfig);
            } catch (RecordingFailedException e) {
                e.printStackTrace();
            }
            // You can also show a toast message or update a UI element to indicate that the recording has started
        }
    }

    private String generateFilename() {
        return "arcore_recording.mp4";
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        filePointsCloud = "CloudPoints" + System.currentTimeMillis() + ".ply";
        filePointsLog = "Log_CloudPoints" + System.currentTimeMillis() + ".txt";
        fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";
        toggleRecording();
    }
}