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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.Debugger;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This represents a single client, usually a Dalvik VM process.
 *
 * <p>This class gives access to basic client information, as well as methods to perform actions on
 * the client.
 *
 * <p>More detailed information, usually updated in real time, can be access through the {@link
 * ClientData} class. Each <code>Client</code> object has its own <code>ClientData</code> accessed
 * through {@link #getClientData()}.
 */
public interface Client {

    int SERVER_PROTOCOL_VERSION = 1;

    /** Client change bit mask: application name change */
    int CHANGE_NAME = 0x0001;
    /** Client change bit mask: debugger status change */
    int CHANGE_DEBUGGER_STATUS = 0x0002;
    /** Client change bit mask: debugger port change */
    int CHANGE_PORT = 0x0004;
    /** Client change bit mask: thread update flag change */
    int CHANGE_THREAD_MODE = 0x0008;
    /** Client change bit mask: thread data updated */
    int CHANGE_THREAD_DATA = 0x0010;
    /** Client change bit mask: heap update flag change */
    int CHANGE_HEAP_MODE = 0x0020;
    /** Client change bit mask: head data updated */
    int CHANGE_HEAP_DATA = 0x0040;
    /** Client change bit mask: native heap data updated */
    int CHANGE_NATIVE_HEAP_DATA = 0x0080;
    /** Client change bit mask: thread stack trace updated */
    int CHANGE_THREAD_STACKTRACE = 0x0100;
    /** Client change bit mask: allocation information updated */
    int CHANGE_HEAP_ALLOCATIONS = 0x0200;
    /** Client change bit mask: allocation information updated */
    int CHANGE_HEAP_ALLOCATION_STATUS = 0x0400;
    /** Client change bit mask: allocation information updated */
    int CHANGE_METHOD_PROFILING_STATUS = 0x0800;
    /** Client change bit mask: hprof data updated */
    int CHANGE_HPROF = 0x1000;

    /**
     * Client change bit mask: combination of {@link Client#CHANGE_NAME}, {@link
     * Client#CHANGE_DEBUGGER_STATUS}, and {@link Client#CHANGE_PORT}.
     */
    int CHANGE_INFO = CHANGE_NAME | CHANGE_DEBUGGER_STATUS | CHANGE_PORT;

    /** Returns the {@link IDevice} on which this Client is running. */
    IDevice getDevice();

    /**
     * Returns <code>true</code> if the client VM is DDM-aware.
     *
     * <p>Calling here is only allowed after the connection has been established.
     */
    boolean isDdmAware();

    /** Returns the {@link ClientData} object containing this client information. */
    @NonNull
    ClientData getClientData();

    /**
     * Sends a kill message to the VM. This doesn't necessarily work if the VM is in a crashed
     * state.
     */
    void kill();

    /** Returns whether this {@link ClientImpl} has a valid connection to the application VM. */
    boolean isValid();

    ///////////////////////// DEBUGGER METHODS ////////////////////////////////////////////

    /** Returns the debugger port for this client. */
    int getDebuggerListenPort();

    /** Returns <code>true</code> if a debugger is currently attached to the client. */
    boolean isDebuggerAttached();

    ///////////////////////// PROFILER METHODS ///////////////////////////////////////////
    /** Forces the client to execute its garbage collector. */
    void executeGarbageCollector();

    void startMethodTracer() throws IOException;

    void stopMethodTracer() throws IOException;

    void startSamplingProfiler(int samplingInterval, TimeUnit timeUnit) throws IOException;

    void stopSamplingProfiler() throws IOException;

    /**
     * Sends a request to the VM to send the information about all the allocations that have
     * happened since the call to {@link #enableAllocationTracker(boolean)} with <var>enable</var>
     * set to <code>null</code>. This is asynchronous.
     *
     * <p>The allocation information can be accessed by {@link ClientData#getAllocations()}. The
     * notification that the new data is available will be received through {@link
     * AndroidDebugBridge.IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>
     * changeMask</code> containing the mask {@link #CHANGE_HEAP_ALLOCATIONS}.
     */
    void requestAllocationDetails();

    /**
     * Enables or disables the Allocation tracker for this client.
     *
     * <p>If enabled, the VM will start tracking allocation information. A call to {@link
     * #requestAllocationDetails()} will make the VM sends the information about all the allocations
     * that happened between the enabling and the request.
     *
     * @param enable
     * @see #requestAllocationDetails()
     */
    void enableAllocationTracker(boolean enabled);

    /**
     * Debugger VM mirrors can exit behind DDMLib's back, leading to various race or perma-{@link
     * Client} loss conditions. We need to notify DDMLib that the debugger currently attached is
     * exiting and killing its VM mirror connection.
     */
    void notifyVmMirrorExited();

    void listViewRoots(DebugViewDumpHandler replyHandler) throws IOException;

    void captureView(
            @NonNull String viewRoot, @NonNull String view, @NonNull DebugViewDumpHandler handler)
            throws IOException;

    void dumpViewHierarchy(
            @NonNull String viewRoot,
            boolean skipChildren,
            boolean includeProperties,
            boolean useV2,
            @NonNull DebugViewDumpHandler handler)
            throws IOException;

    void dumpDisplayList(@NonNull String viewRoot, @NonNull String view) throws IOException;

}

