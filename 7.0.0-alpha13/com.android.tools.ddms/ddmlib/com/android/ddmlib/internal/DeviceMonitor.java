/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.ClientTracker;
import com.android.ddmlib.CommandFailedException;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.Log;
import com.android.ddmlib.internal.jdwp.JdwpProxyServer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link DeviceMonitor} monitors devices attached to adb.
 *
 * <p>On one thread, it runs the {@link DeviceListMonitorTask}. This establishes a
 * socket connection to the adb host, and issues a {@link DeviceListMonitorTask.ADB_TRACK_DEVICES_COMMAND}. It then
 * monitors that socket for all changes about device connection and device state. If {@link AndroidDebugBridge::getClientSupport}
 * is not enabled the {@link DeviceClientMonitorTask} is not run, and devices are not registered.
 *
 * <p>On another thread it runs the {@link DeviceClientMonitorTask}. This establishes
 * a socket connection to the adb host per registered device and issues a {@link DeviceClientMonitorTask.ADB_TRACK_JDWP_COMMAND}.
 * On this connection, it monitors active clients on the device. Note: a single thread monitors jdwp connections from all devices.
 * The different socket connections to adb (one per device) are multiplexed over a single selector.
 */
public final class DeviceMonitor implements ClientTracker {
    private final AndroidDebugBridge mServer;
    private DeviceListMonitorTask mDeviceListMonitorTask;
    @Nullable
    private DeviceClientMonitorTask myDeviceClientMonitorTask;
    private JdwpProxyServer mJdwpProxy;
    private final Object mDevicesGuard = new Object();

    @GuardedBy("mDevicesGuard")
    private ImmutableList<DeviceImpl> mDevices = ImmutableList.of();

    /**
     * Creates a new {@link DeviceMonitor} object and links it to the running {@link
     * AndroidDebugBridge} object.
     *
     * @param server the running {@link AndroidDebugBridge}.
     */
    public DeviceMonitor(@NonNull AndroidDebugBridge server) {
        mServer = server;
    }

    /** Starts the monitoring. */
    public void start() {
        try {
            if (DdmPreferences.isJdwpProxyEnabled()) {
                mJdwpProxy =
                        new JdwpProxyServer(
                                DdmPreferences.getJdwpProxyPort(), this::jdwpProxyChangedState);
                mJdwpProxy.start();
            }

            // To terminate thread call stop on each respective task.
            mDeviceListMonitorTask = new DeviceListMonitorTask(mServer, new DeviceListUpdateListener());
            if (AndroidDebugBridge.getClientSupport()) {
                myDeviceClientMonitorTask = new DeviceClientMonitorTask();
                new Thread(myDeviceClientMonitorTask, "Device Client Monitor").start();
            }
            new Thread(mDeviceListMonitorTask, "Device List Monitor").start(); //$NON-NLS-1$
        }
        catch (IOException ex) {
            // Not expected.
            Log.e("DeviceMonitor", ex);
        }
    }

    private void jdwpProxyChangedState() {
        DeviceImpl[] devices;
        synchronized (mDevicesGuard) {
            devices = mDevices.toArray(new DeviceImpl[0]);
        }
        for (DeviceImpl device : devices) {
            trackDeviceToDropAndReopen(device);
        }
    }

    /**
     * Stops the monitoring.
     */
    public void stop() {
        if (mJdwpProxy != null) {
            mJdwpProxy.stop();
        }

        if (mDeviceListMonitorTask != null) {
            mDeviceListMonitorTask.stop();
        }

        if (myDeviceClientMonitorTask != null) {
            myDeviceClientMonitorTask.stop();
        }
    }

    /** Returns whether the monitor is currently connected to the debug bridge server. */
    public boolean isMonitoring() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.isMonitoring();
    }

    public int getConnectionAttemptCount() {
        return mDeviceListMonitorTask == null
                ? 0
                : mDeviceListMonitorTask.getConnectionAttemptCount();
    }

    public int getRestartAttemptCount() {
        return mDeviceListMonitorTask == null ? 0 : mDeviceListMonitorTask.getRestartAttemptCount();
    }

    public boolean hasInitialDeviceList() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.hasInitialDeviceList();
    }

    /** Returns the devices. */
    @NonNull
    public IDevice[] getDevices() {
        ImmutableList<DeviceImpl> devices;
        synchronized (mDevicesGuard) {
            devices = mDevices;
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return devices.toArray(new IDevice[0]);
    }

    @NonNull
    AndroidDebugBridge getServer() {
        return mServer;
    }

    @Override
    public void trackClientToDropAndReopen(@NonNull ClientImpl client) {
        assert myDeviceClientMonitorTask != null;
        myDeviceClientMonitorTask.registerClientToDropAndReopen(client);
    }

    @Override
    public void trackDisconnectedClient(@NonNull ClientImpl client) {
        assert myDeviceClientMonitorTask != null;
        myDeviceClientMonitorTask.free(client);
    }

    @Override
    public void trackDeviceToDropAndReopen(@NonNull DeviceImpl device) {
        boolean hasDevice;
        synchronized (mDevicesGuard) {
            hasDevice = mDevices.contains(device);
        }
        // restart the monitoring of that device
        if (hasDevice && AndroidDebugBridge.getClientSupport() && myDeviceClientMonitorTask != null) {
            Log.d("DeviceMonitor", "Restarting monitoring service for " + device);
            if (!myDeviceClientMonitorTask.register(device)) {
                Log.e("DeviceMonitor", "Failed to start monitoring " + device.getSerialNumber());
            }
        }
    }

    /**
     * Returns an {@link ImmutableList} containing elements from the original collection and
     * elements from the toAdd collection without elements from the toRemove collection.
     */
    private static ImmutableList<DeviceImpl> addRemove(
            Collection<DeviceImpl> original,
            Collection<IDevice> toAdd,
            Collection<IDevice> toRemove) {
        Set<IDevice> removed = Sets.newHashSet(toRemove);
        ImmutableList.Builder<DeviceImpl> resultBuilder = ImmutableList.builder();
        for (DeviceImpl next : original) {
            if (!removed.contains(next)) {
                resultBuilder.add(next);
            }
        }
        for (IDevice next : toAdd) {
            if (next instanceof DeviceImpl) {
                resultBuilder.add((DeviceImpl) next);
            }
        }
        return resultBuilder.build();
    }

    /** Updates the device list with the new items received from the monitoring service. */
    private void updateDevices(@NonNull List<DeviceImpl> newList) {
        ImmutableList<DeviceImpl> oldDevices;
        synchronized (mDevicesGuard) {
            oldDevices = mDevices;
        }
        DeviceListComparisonResult result = DeviceListComparisonResult.compare(oldDevices, newList);
        ImmutableList<DeviceImpl> newDevices = addRemove(oldDevices, result.added, result.removed);
        synchronized (mDevicesGuard) {
            mDevices = newDevices;
        }

        for (IDevice device : result.removed) {
            removeDevice((DeviceImpl) device);
            AndroidDebugBridge.deviceDisconnected(device);
        }

        List<DeviceImpl> newlyOnline = Lists.newArrayListWithExpectedSize(newDevices.size());

        for (Map.Entry<IDevice, DeviceState> entry : result.updated.entrySet()) {
            DeviceImpl device = (DeviceImpl) entry.getKey();
            device.setState(entry.getValue());
            device.update(IDevice.CHANGE_STATE);

            if (device.isOnline()) {
                newlyOnline.add(device);
            }
        }

        for (IDevice device : result.added) {
            AndroidDebugBridge.deviceConnected(device);
            if (device.isOnline()) {
                newlyOnline.add((DeviceImpl) device);
            }
        }

        if (AndroidDebugBridge.getClientSupport()) {
            for (DeviceImpl device : newlyOnline) {
                if (!myDeviceClientMonitorTask.register(device)) {
                    Log.e(
                            "DeviceMonitor",
                            "Failed to start monitoring " + device.getSerialNumber());
                }
            }
        }

        for (DeviceImpl device : newlyOnline) {
            setProperties(device);

            // Initiate a property fetch so that future requests can be served out of this cache.
            // This is necessary for backwards compatibility
            device.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL);
        }
    }

    private void removeDevice(@NonNull DeviceImpl device) {
        device.setState(DeviceState.DISCONNECTED);
        device.clearClientList();

        SocketChannel channel = device.getClientMonitoringSocket();
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // doesn't really matter if the close fails.
            }
        }
    }

    private static void setProperties(@NonNull DeviceImpl device) {
        if (!device.isEmulator()) {
            return;
        }

        EmulatorConsole console = EmulatorConsole.getConsole(device);

        if (console == null) {
            return;
        }

        device.setAvdName(console.getAvdName());

        try {
            device.setAvdPath(console.getAvdPath());
        } catch (CommandFailedException exception) {
            Log.e("DeviceMonitor", exception);
        }

        console.close();
    }

    private class DeviceListUpdateListener implements DeviceListMonitorTask.UpdateListener {
        @Override
        public void connectionError(@NonNull Exception e) {
            // TODO(b/37104675): Clearing the device list in response to an exception is probably the wrong thing to do.
            ImmutableList<DeviceImpl> devices;
            synchronized (mDevicesGuard) {
                devices = mDevices;
                mDevices = ImmutableList.of();
            }
            for (DeviceImpl device : devices) {
                removeDevice(device);
                AndroidDebugBridge.deviceDisconnected(device);
            }
        }

        @Override
        public void deviceListUpdate(@NonNull Map<String, DeviceState> devices) {
            List<DeviceImpl> l = Lists.newArrayListWithExpectedSize(devices.size());
            for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
                l.add(new DeviceImpl(DeviceMonitor.this, entry.getKey(), entry.getValue()));
            }
            // now merge the new devices with the old ones.
            updateDevices(l);
        }
    }

    @VisibleForTesting
    public static class DeviceListComparisonResult {
        @NonNull public final Map<IDevice, DeviceState> updated;
        @NonNull public final List<IDevice> added;
        @NonNull public final List<IDevice> removed;

        private DeviceListComparisonResult(
                @NonNull Map<IDevice, DeviceState> updated,
                @NonNull List<IDevice> added,
                @NonNull List<IDevice> removed) {
            this.updated = updated;
            this.added = added;
            this.removed = removed;
        }

        @NonNull
        public static DeviceListComparisonResult compare(
                @NonNull List<? extends IDevice> previous,
                @NonNull List<? extends IDevice> current) {
            current = Lists.newArrayList(current);

            final Map<IDevice, DeviceState> updated =
                    Maps.newHashMapWithExpectedSize(current.size());
            final List<IDevice> added = Lists.newArrayListWithExpectedSize(1);
            final List<IDevice> removed = Lists.newArrayListWithExpectedSize(1);

            for (IDevice device : previous) {
                IDevice currentDevice = find(current, device);
                if (currentDevice != null) {
                    if (currentDevice.getState() != device.getState()) {
                        updated.put(device, currentDevice.getState());
                    }
                    current.remove(currentDevice);
                } else {
                    removed.add(device);
                }
            }

            added.addAll(current);

            return new DeviceListComparisonResult(updated, added, removed);
        }

        @Nullable
        private static IDevice find(
                @NonNull List<? extends IDevice> devices, @NonNull IDevice device) {
            for (IDevice d : devices) {
                if (d.getSerialNumber().equals(device.getSerialNumber())) {
                    return d;
                }
            }

            return null;
        }
    }
}
