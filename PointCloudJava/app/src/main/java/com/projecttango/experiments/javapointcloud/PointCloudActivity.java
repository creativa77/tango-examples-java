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

package com.projecttango.experiments.javapointcloud;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Simple Activity that exercises the Tango code and nothing else
 */
public class PointCloudActivity extends Activity {

    private static final String TAG = PointCloudActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;

    private boolean mIsTangoServiceConnected;
    private TangoPoseData mPose;
    private static final int UPDATE_INTERVAL_MS = 100;
    public static Object poseLock = new Object();
    public static Object depthLock = new Object();

    private Handler mHandler = new Handler();
    private static final int POSE_POLLING_PERIOD_MS = 33;
    private boolean mIsLocalized =false;
    private double mLastLocalizedTimestamp = 0;

    private static final TangoCoordinateFramePair ADF_DEVICE_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);
    private static final TangoCoordinateFramePair START_DEVICE_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);
    private static final TangoCoordinateFramePair ADF_START_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpoint_cloud);
        setTitle(R.string.app_name);

        mTango = new Tango(this);
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_EXPERIMENTAL_HIGH_ACCURACY_SMALL_SCALE_ADF,
                true);
        mIsTangoServiceConnected = false;

    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            mTango.disconnect();
            mIsTangoServiceConnected = false;
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsTangoServiceConnected) {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE + 1);
        }
        Log.i(TAG, "onResumed");
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
            try {
                setTangoListeners();
                start();
            } catch (TangoErrorException e) {
                Toast.makeText(this, R.string.TangoError, Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                        Toast.LENGTH_SHORT).show();
            }
            if (Tango.hasPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE) &&
                    Tango.hasPermission(this, Tango.PERMISSIONTYPE_MOTION_TRACKING)) {
                try {
                    mTango.connect(mConfig);
                    mIsTangoServiceConnected = true;
                } catch (TangoOutOfDateException e) {
                    Toast.makeText(getApplicationContext(), R.string.TangoOutOfDateException,
                            Toast.LENGTH_SHORT).show();
                } catch (TangoErrorException e) {
                    Toast.makeText(getApplicationContext(), R.string.TangoError,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void start() {
        mHandler.postDelayed(mGetPose, POSE_POLLING_PERIOD_MS);
    }

    private Runnable mGetPose = new Runnable() {
        @Override
        public void run() {
            if (mTango != null) {
                try {
                    TangoPoseData pose;
                    if (mIsLocalized) {
                        pose = mTango.getPoseAtTime(0, ADF_DEVICE_FRAME_PAIR);
                    } else {
                        pose = mTango.getPoseAtTime(0, START_DEVICE_FRAME_PAIR);
                    }
                    if (TangoPoseData.POSE_VALID == pose.statusCode) {
                        float[] translation = pose.getTranslationAsFloats();
                        Log.i(TAG, "Device:  x: " + translation[0] + " y: " + translation[1] + " z: " + translation[2]);
                    }




                    pose = mTango.getPoseAtTime(0, ADF_START_FRAME_PAIR);
                    if (TangoPoseData.POSE_VALID == pose.statusCode
                            && pose.timestamp > mLastLocalizedTimestamp) {
                        if (mLastLocalizedTimestamp != 0) {
                            mIsLocalized = true;
                            float[] translation2 = pose.getTranslationAsFloats();

                            Log.i(TAG, "Relcoalized: x: "+ translation2[0] + " y: "+translation2[1] + " z: "+translation2[2]);
                        }
                        mLastLocalizedTimestamp = pose.timestamp;
                    }
                } catch (TangoInvalidException e) {
                    Log.e(TAG, "Error Getting Pose", e);
                }
            }
            mHandler.postDelayed(this, POSE_POLLING_PERIOD_MS);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setTangoListeners() {
        // Configure the Tango coordinate frame pair
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));
        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData tangoPoseData) {
                if(tangoPoseData.statusCode == TangoPoseData.POSE_VALID){
                    if (tangoPoseData.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION &&
                            tangoPoseData.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {




                    } else if (tangoPoseData.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION &&
                            tangoPoseData.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                        Log.d(TAG, "Relocalized");

                    }
                }
            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {

            }

            @Override
            public void onTangoEvent(final TangoEvent event) {

            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }
}
