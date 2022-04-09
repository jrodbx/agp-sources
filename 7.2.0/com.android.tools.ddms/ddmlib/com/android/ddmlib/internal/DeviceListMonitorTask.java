/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
 * This establishes a socket connection to the adb host, and issues a {@link
 * #ADB_TRACK_DEVICES_COMMAND}. It then monitors that socket for all changes about device connection
 * and device state.
 */

@VisibleForTesting
public class DeviceListMonitorTask implements Runnable {
    private static final String ADB_TRACK_DEVICES_COMMAND = "host:track-devices";

    private final byte[] mLengthBuffer = new byte[4];

    private final AndroidDebugBridge mBridge;
    private final UpdateListener mListener;

    private SocketChannel mAdbConnection = null;
    private boolean mMonitoring = false;
    private int mConnectionAttempt = 0;
    private int mRestartAttemptCount = 0;
    private Stopwatch mAdbDisconnectionStopwatch;
    private boolean mInitialDeviceListDone = false;

    private volatile boolean mQuit;

    interface UpdateListener {
        void connectionError(@NonNull Exception e);

        void deviceListUpdate(@NonNull Map<String, IDevice.DeviceState> devices);
    }

    DeviceListMonitorTask(@NonNull AndroidDebugBridge bridge, @NonNull UpdateListener listener) {
        mBridge = bridge;
        mListener = listener;
    }

    @Override
    public void run() {
        do {
            if (mAdbConnection == null) {
                Log.d("DeviceMonitor", "Opening adb connection");
                try {
                    mAdbConnection = AndroidDebugBridge.openConnection();
                } catch (IOException exception) {
                    Log.d("DeviceMonitor", "Unable to open connection to ADB server: " + exception);
                }
                if (mAdbConnection == null) {
                    mConnectionAttempt++;

                    // Only log on first retry attempt to avoid spamming logs.
                    if (mConnectionAttempt == 1) {
                        Log.e("DeviceMonitor", "Cannot reach ADB server, attempting to reconnect.");
                        mAdbDisconnectionStopwatch = Stopwatch.createStarted();
                        if (AndroidDebugBridge.isUserManagedAdbMode()) {
                            Log.i(
                                    "DeviceMonitor",
                                    "Will not automatically restart the ADB server because ddmlib is in user managed mode");
                        }
                    }
                    if (!AndroidDebugBridge.isUserManagedAdbMode() && mConnectionAttempt > 10) {
                        if (!mBridge.startAdb(
                                AndroidDebugBridge.DEFAULT_START_ADB_TIMEOUT_MILLIS,
                                TimeUnit.MILLISECONDS)) {
                            mRestartAttemptCount++;
                        } else {
                            Log.i("DeviceMonitor", "adb restarted");
                            mRestartAttemptCount = 0;
                        }
                    }
                    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                } else {
                    if (mConnectionAttempt > 0) {
                        Log.i(
                                "DeviceMonitor",
                                "ADB connection re-established after "
                                        + mAdbDisconnectionStopwatch.elapsed(TimeUnit.SECONDS)
                                        + " seconds.");
                        mAdbDisconnectionStopwatch.reset();
                    } else {
                        Log.i("DeviceMonitor", "Connected to adb for device monitoring");
                    }
                    mConnectionAttempt = 0;
                }
            }

            try {
                if (mAdbConnection != null && !mMonitoring) {
                    mMonitoring = sendDeviceListMonitoringRequest();
                }

                if (mMonitoring) {
                    int length = AdbSocketUtils.readLength(mAdbConnection, mLengthBuffer);

                    if (length >= 0) {
                        // read the incoming message
                        processIncomingDeviceData(length);

                        // flag the fact that we have build the list at least once.
                        mInitialDeviceListDone = true;
                    }
                }
            } catch (AsynchronousCloseException ace) {
                // this happens because of a call to Quit. We do nothing, and the loop will break.
            } catch (IOException | TimeoutException ex) {
                handleExceptionInMonitorLoop(ex);
            }
        } while (!mQuit);
    }

    private boolean sendDeviceListMonitoringRequest() throws TimeoutException, IOException {
        byte[] request = AdbHelper.formAdbRequest(ADB_TRACK_DEVICES_COMMAND);

        try {
            AdbHelper.write(mAdbConnection, request);
            AdbHelper.AdbResponse resp = AdbHelper.readAdbResponse(mAdbConnection, false);
            if (!resp.okay) {
                // request was refused by adb!
                Log.e("DeviceMonitor", "adb refused request: " + resp.message);
            }

            return resp.okay;
        } catch (IOException e) {
            Log.e("DeviceMonitor", "Sending Tracking request failed!");
            mAdbConnection.close();
            throw e;
        }
    }

    private void handleExceptionInMonitorLoop(@NonNull Exception e) {
        if (!mQuit) {
            if (e instanceof TimeoutException) {
                Log.e("DeviceMonitor", "Adb connection Error: timeout");
            } else {
                Log.e("DeviceMonitor", "Adb connection Error:" + e.getMessage());
            }
            mMonitoring = false;
            if (mAdbConnection != null) {
                try {
                    mAdbConnection.close();
                } catch (IOException ioe) {
                    // we can safely ignore that one.
                }
                mAdbConnection = null;

                mListener.connectionError(e);
            }
        }
    }

    /** Processes an incoming device message from the socket */
    private void processIncomingDeviceData(int length) throws IOException {
        Map<String, IDevice.DeviceState> result;
        if (length <= 0) {
            result = Collections.emptyMap();
        } else {
            String response = AdbSocketUtils.read(mAdbConnection, new byte[length]);
            result = parseDeviceListResponse(response);
        }

        mListener.deviceListUpdate(result);
    }

    @VisibleForTesting
    public static Map<String, IDevice.DeviceState> parseDeviceListResponse(
            @Nullable String result) {
        Map<String, IDevice.DeviceState> deviceStateMap = Maps.newHashMap();
        String[] devices = result == null ? new String[0] : result.split("\n"); // $NON-NLS-1$

        for (String d : devices) {
            String[] param = d.split("\t"); // $NON-NLS-1$
            if (param.length == 2) {
                // new adb uses only serial numbers to identify devices
                deviceStateMap.put(param[0], IDevice.DeviceState.getState(param[1]));
            }
        }
        return deviceStateMap;
    }

    boolean isMonitoring() {
        return mMonitoring;
    }

    boolean hasInitialDeviceList() {
        return mInitialDeviceListDone;
    }

    int getConnectionAttemptCount() {
        return mConnectionAttempt;
    }

    int getRestartAttemptCount() {
        return mRestartAttemptCount;
    }

    public void stop() {
        mQuit = true;

        // wakeup the main loop thread by closing the main connection to adb.
        if (mAdbConnection != null) {
            try {
                mAdbConnection.close();
            } catch (IOException ignored) {
            }
        }
    }
}
