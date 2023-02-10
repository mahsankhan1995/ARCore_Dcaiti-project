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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collection;

public class MainActivity1 extends AppCompatActivity implements Scene.OnUpdateListener {
    private ArFragment arFragment;

    private String filePointsCloud = "";
    private String fileVideo = "";
    private Button scanButton;

    private boolean addHeader = true;

    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main1);

        // Initialize variables
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Set up a button click listener to start the scan
        scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // String date= new SimpleDateFormat("MM-dd hh:mm a").format(new java.util.Date());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        filePointsCloud = "CloudPoints" + System.currentTimeMillis() + ".ply";
                        fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";
                        toggleRecording();
                    } else {
                        ActivityCompat.requestPermissions(MainActivity1.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 40);
                    }
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

    private void onUpdateFrame() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        FloatBuffer pointCloudData = frame.acquirePointCloud().getPoints();

        String strHeader = "ply\n" +
                "format ascii 1.0\n" +
                "comment Point Cloud for 3D Reconstruction\n" +
                "element vertex 25294.0\n" +
                "property float x\n" +
                "property float y\n" +
                "property float z\n" +
                "end_header\n";

        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePointsCloud);
            FileOutputStream outputStream = new FileOutputStream(file, true);
            if (addHeader) {
                outputStream.write(strHeader.getBytes());
                addHeader = false;
            }

            for (int i = 0; i < pointCloudData.capacity() / 3; i++) {
                float x = pointCloudData.get(3 * i);
                float y = pointCloudData.get(3 * i + 1);
                float z = pointCloudData.get(3 * i + 2);
                String str = x + " " + y + " " + z + "\n ";
                Log.d("outputStream", str);
                outputStream.write(str.getBytes());
            }
            outputStream.close();
        } catch (IOException e) {
            Log.d("outputStream-", e.getMessage().toString());
            e.printStackTrace();
        }
    }


    private void toggleRecording() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileVideo);

        if (isRecording) {
            // Stop recording
            isRecording = false;
            scanButton.setText("Record");
            try {
                arFragment.getArSceneView().getSession().stopRecording();
            } catch (RecordingFailedException e) {
                e.printStackTrace();
            }
            // You can also show a toast message or update a UI element to indicate that the recording has stopped
        } else {
            // Start recording
            scanButton.setText("Stop");
            isRecording = true;
            addHeader = true;
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
        fileVideo = "Recording" + System.currentTimeMillis() + ".mp4";
        toggleRecording();
    }
}