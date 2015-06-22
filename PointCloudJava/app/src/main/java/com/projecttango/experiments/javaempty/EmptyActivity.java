/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.projecttango.experiments.javaempty;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Simple Activity that exercises the Tango code and nothing else
 */
public class EmptyActivity extends Activity {

    private static final String TAG = EmptyActivity.class.getSimpleName();
    private Tango mTango;
    private TangoConfig mConfig;

    private Button buttonStart, buttonStop;
    private CheckBox chkMotionTracking, chkDepthPerception, chkAreaLearning, chkCameraPreview;

    private boolean mIsTangoServiceConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        buttonStart = (Button) findViewById(R.id.btn_start);
        buttonStop = (Button) findViewById(R.id.btn_stop);
        chkAreaLearning = (CheckBox) findViewById(R.id.chk_area_learning);
        chkCameraPreview = (CheckBox) findViewById(R.id.chk_camera_preview);
        chkMotionTracking = (CheckBox) findViewById(R.id.chk_motion_tracking);
        chkDepthPerception = (CheckBox) findViewById(R.id.chk_depth_perception);

        mTango = new Tango(this);
        mIsTangoServiceConnected = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTango();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsTangoServiceConnected && !hasPermissions()) {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE + 1);
        }
        Log.i(TAG, "onResumed");
    }

    private boolean hasPermissions() {
        return Tango.hasPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE) &&
                Tango.hasPermission(this, Tango.PERMISSIONTYPE_MOTION_TRACKING);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            Log.i(TAG, "Triggered");
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.motiontrackingpermission, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (hasPermissions()) {
                try {
                    buttonStart.setEnabled(true);
                } catch (TangoOutOfDateException e) {
                    Toast.makeText(getApplicationContext(), R.string.TangoOutOfDateException,
                            Toast.LENGTH_SHORT).show();
                } catch (TangoErrorException e) {
                    Toast.makeText(getApplicationContext(), R.string.TangoError,
                            Toast.LENGTH_SHORT).show();
                } catch (SecurityException e) {
                    Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Assumed called after permissions are granted.
     */
    private void startTango() {
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, chkDepthPerception.isChecked());
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, chkMotionTracking.isChecked());
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, chkAreaLearning.isChecked());
        mTango.connect(mConfig);
        setTangoListeners();
        mIsTangoServiceConnected = true;
    }

    private void stopTango() {
        if (mIsTangoServiceConnected) {
            try {
                mTango.disconnect();
                mIsTangoServiceConnected = false;
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setTangoListeners() {
        // Configure the Tango coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {

            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {

            }

            @Override
            public void onTangoEvent(final TangoEvent event) {

            }

            @Override
            public void onFrameAvailable(int cameraId) {

            }
        });
    }

    public void startClicked(View view) {
        startTango();
        setViewStates(true);
    }

    public void stopClicked(View view) {
        stopTango();
        setViewStates(false);
    }

    private void setViewStates(boolean tangoRunning) {
        buttonStart.setEnabled(!tangoRunning);
        buttonStop.setEnabled(tangoRunning);
        chkDepthPerception.setEnabled(!tangoRunning);
        chkCameraPreview.setEnabled(!tangoRunning);
        chkMotionTracking.setEnabled(!tangoRunning);
        chkAreaLearning.setEnabled(!tangoRunning);
    }
}
