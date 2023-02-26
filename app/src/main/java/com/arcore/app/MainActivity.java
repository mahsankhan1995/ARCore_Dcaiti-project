package com.arcore.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.ArFragment;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity implements Scene.OnUpdateListener {
    private ArFragment arFragment;

    private String filePointsCloud = "";
    private String filePointsLog = "";

    private String fileVideo = "";
    private Button scanButton;
    private TextView tvState;

    private int totalPointsCount = 0;

    private boolean addHeader = true;
    private boolean isRecording = false;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private StringBuilder vertexData = new StringBuilder();
    private LocalDateTime now = LocalDateTime.now();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Variables
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Set up a button click listener to start the scan
        scanButton = findViewById(R.id.scan_button);
        tvState = findViewById(R.id.tv_state);

        scanButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    filePointsCloud = "CloudPoints" + System.currentTimeMillis() + ".ply";
                    filePointsLog = "CloudPointsLog" + System.currentTimeMillis() + ".txt";
                    fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";

                    toggleRecording();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 40);
                }
            }
        });

        arFragment.getArSceneView().getScene().addOnUpdateListener(this);
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onUpdateFrame() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        FloatBuffer pointCloudData = frame.acquirePointCloud().getPoints();

        String strHeader = "ply\n" +
                "format ascii 1.0\n" +
                "comment Point Cloud for 3D Reconstruction\n" +
                "element vertex &\n" +
                "property float x\n" +
                "property float y\n" +
                "property float z\n" +
                "end_header\n";
        try {
            File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePointsLog);
            FileOutputStream logOutputStream = new FileOutputStream(logFile, true);

            if (addHeader) {
                vertexData.append(strHeader);
                addHeader = false;
            }

            for (int i = 0; i < pointCloudData.capacity() / 3; i++) {
                float x = pointCloudData.get(3 * i);
                float y = pointCloudData.get(3 * i + 1);
                float z = pointCloudData.get(3 * i + 2);

                String str = x + " " + y + " " + z + "\n ";

                totalPointsCount = totalPointsCount + 1;
                vertexData.append(str);

                long frameTimeStamp = frame.acquirePointCloud().getTimestamp();
                long frameCameraTimeStamp = frame.getAndroidCameraTimestamp();
                LightEstimate frameLightEstimate = frame.getLightEstimate();
                Pose frameAndroidSensorPose = frame.getAndroidSensorPose();
                IntBuffer frameIds = frame.acquirePointCloud().getIds();
                TrackingState trackingState = frame.getCamera().getTrackingState();

                String logData = x + " " + y + " " + z + "Frame Time:" + frameTimeStamp + " Frame Camera Time Stamp:" + frameCameraTimeStamp + " Frame Light Estimate:" + frameLightEstimate.toString() + " Frame Sensor Pose:" + frameAndroidSensorPose.toString() + " Frame ID:" + frameIds.toString() + " Time:" + formatter.format(now) + "\n ";

                if (trackingState != null) {
                    if (trackingState == TrackingState.PAUSED) {
                        logData = logData + " Tracking State: Paused ";
                    } else if (trackingState == TrackingState.STOPPED) {
                        logData = logData + " Tracking State: Stopped ";
                    } else {
                        logData = logData + " Tracking State: Tracking ";
                    }
                    tvState.setText("Tracking State: " + trackingState);
                }

                logOutputStream.write(logData.getBytes());
            }
        } catch (Exception e) {
            Log.d("outputStream-", e.getMessage().toString());
            e.printStackTrace();
        }
    }

    private void toggleRecording() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileVideo);
        if (isRecording) {
            try {
                File filePly = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePointsCloud);
                FileOutputStream outputStream = null;

                outputStream = new FileOutputStream(filePly, true);
                String finalStr = vertexData.toString().replaceAll("&", totalPointsCount + "");
                outputStream.write(finalStr.getBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Stop Recording
            isRecording = false;
            tvState.setVisibility(View.INVISIBLE);
            scanButton.setText("Record");

            try {
                arFragment.getArSceneView().getSession().stopRecording();
            } catch (RecordingFailedException e) {
                e.printStackTrace();
            }
            // You can also show a toast message or update a UI element to indicate that the recording has stopped
        } else {
            // Start Recording
            scanButton.setText("Stop");
            tvState.setVisibility(View.VISIBLE);

            totalPointsCount = 0;

            isRecording = true;
            addHeader = true;

            RecordingConfig recordingConfig = new RecordingConfig(arFragment.getArSceneView().getSession());
            recordingConfig.setRecordingRotation(90);
            recordingConfig.setMp4DatasetUri(Uri.fromFile(file));
            try {
                arFragment.getArSceneView().getSession().startRecording(recordingConfig);
            } catch (RecordingFailedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        filePointsCloud = "CloudPoints" + System.currentTimeMillis() + ".ply";
        fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";
        toggleRecording();
    }
}