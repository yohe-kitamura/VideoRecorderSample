/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.pockeps.videorecordersample;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final String TAG = "Recorder";
    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;
    @Nullable
    private File mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
    private boolean isRecording = false;
    private Button captureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        mPreview = findViewById(R.id.surface_view);
        captureButton = findViewById(R.id.button_capture);
    }

    /**
     * The capture button controls all user interaction. When recording, the button click
     * stops recording, releases {@link android.media.MediaRecorder} and {@link android.hardware.Camera}. When not recording,
     * it prepares the {@link android.media.MediaRecorder} and starts recording.
     *
     * @param view the view generating the event.
     */
    public void onCaptureClick(View view) {
        if (isRecording) {
            // 録画停止
            try {
                mMediaRecorder.stop();
            } catch (RuntimeException e) {
                 Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                if (mOutputFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    mOutputFile.delete();
                }
            }
            releaseMediaRecorder();
            mCamera.lock();

            setCaptureButtonText("Capture");
            isRecording = false;
            releaseCamera();

        } else {
            new MediaPrepareTask().execute(null, null, null);
        }
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();
        releaseCamera();
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;

            mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean prepareVideoRecorder(@NonNull File outPutFile) {

        // Cameraセットアップ
        int cameraId = CameraHelper.getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
        if (cameraId < 0) {
            return false;
        }
        mCamera = Camera.open(cameraId);
        Camera.Size optimalSize = CameraHelper.getOptimalSize(mCamera.getParameters(), mPreview);
        int cameraRotation = CameraHelper.getCameraDisplayOrientation(this, cameraId);
        try {
            CameraHelper.setUpCameraInstance(mCamera, optimalSize,
                    cameraRotation);
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }

        // Recorder生成
        mCamera.unlock();
        mMediaRecorder = CameraHelper.createMediaRecorder(mCamera, cameraRotation, outPutFile, optimalSize);

        // Recorder準備
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    public void onClickVideoPlay(View view) {
        startActivity(new Intent(this, VideoPlayerActivity.class));
    }

    private class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // ビデオ録画準備
            if (mOutputFile != null && prepareVideoRecorder(mOutputFile)) {
                // ビデオ録画開始
                mMediaRecorder.start();

                isRecording = true;
            } else {
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            setCaptureButtonText("Stop");

        }
    }

}
