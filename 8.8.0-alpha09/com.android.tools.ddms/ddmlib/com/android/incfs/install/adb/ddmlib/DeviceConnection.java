/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.incfs.install.adb.ddmlib;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.incfs.install.IDeviceConnection;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.utils.ILogger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

/**
 * Represents a connection to a device through ADB for use with {@link
 * IncrementalInstallSession.Builder#execute(Executor, IDeviceConnection.Factory, ILogger)}.
 */
public class DeviceConnection implements IDeviceConnection {
    /**
     * Creates a factory for creating a connection to the device.
     *
     * @param deviceSerialNumber the serial of the device to talk to.
     */
    public static Factory getFactory(@NonNull String deviceSerialNumber) throws IOException {
        final AndroidDebugBridge adb = AndroidDebugBridge.getBridge();
        if (adb == null) {
            throw new IOException("Unable to connect to adb");
        }
        for (final IDevice device : adb.getDevices()) {
            if (deviceSerialNumber.equals(device.getSerialNumber())) {
                return new Factory(device);
            }
        }
        throw new IOException("Failed to find device with serial \"" + deviceSerialNumber + "\"");
    }

    public static class Factory implements IDeviceConnection.Factory {
        private final IDevice mDevice;

        private Factory(@NonNull IDevice device) {
            mDevice = device;
        }

        @Override
        public IDeviceConnection connectToService(
                @NonNull String service, @NonNull String[] parameters) throws IOException {
            final SocketChannel channel;
            try {
                channel = mDevice.rawBinder(service, parameters);
            } catch (AdbCommandRejectedException | TimeoutException e) {
                throw new IOException(
                        String.format(
                                "Failed invoking binder command \"%s %s\"",
                                service, String.join(" ", parameters)),
                        e);
            }
            channel.configureBlocking(false);
            return new DeviceConnection(channel);
        }
    }

    @NonNull private final SocketChannel mChannel;

    @NonNull private final Selector mReadSelector;
    @NonNull private final Selector mWriteSelector;

    private DeviceConnection(@NonNull SocketChannel channel) throws IOException {
        mChannel = channel;
        mChannel.configureBlocking(false);
        mReadSelector = Selector.open();
        mChannel.register(mReadSelector, SelectionKey.OP_READ);
        mWriteSelector = Selector.open();
        mChannel.register(mWriteSelector, SelectionKey.OP_WRITE);
    }

    @Override
    public int read(@NonNull ByteBuffer dst, long timeoutMs) throws IOException {
        mReadSelector.select(timeoutMs);
        return mChannel.read(dst);
    }

    @Override
    public int write(@NonNull ByteBuffer src, long timeoutMs) throws IOException {
        mWriteSelector.select(timeoutMs);
        return mChannel.write(src);
    }

    @Override
    public void close() throws Exception {
        try (Channel c = mChannel;
                Selector r = mReadSelector;
                Selector w = mWriteSelector) {}
    }
}
