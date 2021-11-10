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
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmConstants;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.DebugViewDumpHandler;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.Log;
import com.android.ddmlib.ThreadInfo;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleExit;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleNativeHeap;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleThread;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleViewDebug;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.JdwpAgent;
import com.android.ddmlib.jdwp.JdwpProtocol;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
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
public class ClientImpl extends JdwpAgent implements Client {
    /**
     * Users of mChan avoid synchronization issues by taking a local instance of the channel before
     * using it. This class-level shared instance can be nulled by any thread calling #close() so
     * volatile is needed to ensure threads don't read stale values if mChan.
     */
    private volatile SocketChannel mChan;

    // debugger we're associated with, if any
    private Debugger mDebugger;

    // chunk handlers stash state data in here
    private ClientData mClientData;

    // User interface state.  Changing the value causes a message to be
    // sent to the client.
    private boolean mThreadUpdateEnabled;
    private boolean mHeapInfoUpdateEnabled;
    private boolean mHeapSegmentUpdateEnabled;

    /*
     * Read/write buffers.  We can get large quantities of data from the
     * client, e.g. the response to a "give me the list of all known classes"
     * request from the debugger.  Requests from the debugger, and from us,
     * are much smaller.
     *
     * Pass-through debugger traffic is sent without copying.  "mWriteBuffer"
     * is only used for data generated within Client.
     */
    private static final int INITIAL_BUF_SIZE = 2 * 1024;
    private ByteBuffer mReadBuffer;
    private final int mMaxPacketSize = DdmPreferences.getJdwpMaxPacketSize();

    private DeviceImpl mDevice;

    private int mConnState;

    private static final int ST_INIT = 1;
    private static final int ST_NOT_JDWP = 2;
    private static final int ST_AWAIT_SHAKE = 10;
    private static final int ST_NEED_DDM_PKT = 11;
    private static final int ST_NOT_DDM = 12;
    private static final int ST_READY = 13;
    private static final int ST_ERROR = 20;
    private static final int ST_DISCONNECTED = 21;

    /**
     * Create an object for a new client connection.
     *
     * @param device the device this client belongs to
     * @param chan the connected {@link SocketChannel}.
     * @param pid the client pid.
     */
    public ClientImpl(DeviceImpl device, SocketChannel chan, int pid) {
        super(new JdwpProtocol());
        mDevice = device;
        mChan = chan;
        mReadBuffer = ByteBuffer.allocate(INITIAL_BUF_SIZE);

        mConnState = ST_INIT;

        mClientData = new ClientData(this, pid);

        mThreadUpdateEnabled = DdmPreferences.getInitialThreadUpdate();
        mHeapInfoUpdateEnabled = DdmPreferences.getInitialHeapUpdate();
        mHeapSegmentUpdateEnabled = DdmPreferences.getInitialHeapUpdate();
    }

    /** Returns a string representation of the {@link ClientImpl} object. */
    @Override
    public String toString() {
        return "[Client pid: " + mClientData.getPid() + "]";
    }

    /** Returns the {@link IDevice} on which this Client is running. */
    @Override
    public IDevice getDevice() {
        return mDevice;
    }

    /** Returns the {@link DeviceImpl} on which this Client is running. */
    public DeviceImpl getDeviceImpl() {
        return mDevice;
    }

    /** Returns the debugger port for this client. */
    @Override
    public int getDebuggerListenPort() {
        return getDebugger() == null ? -1 : getDebugger().getListenPort();
    }

    /**
     * Returns <code>true</code> if the client VM is DDM-aware.
     *
     * <p>Calling here is only allowed after the connection has been established.
     */
    @Override
    public boolean isDdmAware() {
        switch (mConnState) {
            case ST_INIT:
            case ST_NOT_JDWP:
            case ST_AWAIT_SHAKE:
            case ST_NEED_DDM_PKT:
            case ST_NOT_DDM:
            case ST_ERROR:
            case ST_DISCONNECTED:
                return false;
            case ST_READY:
                return true;
            default:
                assert false;
                return false;
        }
    }

    /** Returns <code>true</code> if a debugger is currently attached to the client. */
    @Override
    public boolean isDebuggerAttached() {
        return mDebugger != null && mDebugger.isDebuggerAttached();
    }

    /** Return the Debugger object associated with this client. */
    Debugger getDebugger() {
        return mDebugger;
    }

    /** Returns the {@link ClientData} object containing this client information. */
    @Override
    @NonNull
    public ClientData getClientData() {
        return mClientData;
    }

    /** Forces the client to execute its garbage collector. */
    @Override
    public void executeGarbageCollector() {
        try {
            HandleHeap.sendHPGC(this);
        } catch (IOException ioe) {
            Log.w("ddms", "Send of HPGC message failed");
            // ignore
        }
    }

    /**
     * Toggles method profiling state.
     *
     * @deprecated Use {@link #startMethodTracer()}, {@link #stopMethodTracer()}, {@link
     *     #startSamplingProfiler(int, TimeUnit)} or {@link #stopSamplingProfiler()} instead.
     */
    @Deprecated
    public void toggleMethodProfiling() {
        try {
            switch (mClientData.getMethodProfilingStatus()) {
                case TRACER_ON:
                    stopMethodTracer();
                    break;
                case SAMPLER_ON:
                    stopSamplingProfiler();
                    break;
                case OFF:
                    startMethodTracer();
                    break;
            }
        } catch (IOException e) {
            Log.w("ddms", "Toggle method profiling failed");
            // ignore
        }
    }

    private static int getProfileBufferSize() {
        return DdmPreferences.getProfilerBufferSizeMb() * 1024 * 1024;
    }

    @Override
    public void startMethodTracer() throws IOException {
        boolean canStream = mClientData.hasFeature(ClientData.FEATURE_PROFILING_STREAMING);
        int bufferSize = getProfileBufferSize();
        if (canStream) {
            HandleProfiling.sendMPSS(this, bufferSize, 0 /*flags*/);
        } else {
            String file =
                    "/sdcard/"
                            + mClientData.getClientDescription().replaceAll("\\:.*", "")
                            + DdmConstants.DOT_TRACE;
            HandleProfiling.sendMPRS(this, file, bufferSize, 0 /*flags*/);
        }
    }

    @Override
    public void stopMethodTracer() throws IOException {
        boolean canStream = mClientData.hasFeature(ClientData.FEATURE_PROFILING_STREAMING);

        if (canStream) {
            HandleProfiling.sendMPSE(this);
        } else {
            HandleProfiling.sendMPRE(this);
        }
    }

    @Override
    public void startSamplingProfiler(int samplingInterval, TimeUnit timeUnit) throws IOException {
        int bufferSize = getProfileBufferSize();
        HandleProfiling.sendSPSS(this, bufferSize, samplingInterval, timeUnit);
    }

    @Override
    public void stopSamplingProfiler() throws IOException {
        HandleProfiling.sendSPSE(this);
    }

    public boolean startOpenGlTracing() {
        boolean canTraceOpenGl = mClientData.hasFeature(ClientData.FEATURE_OPENGL_TRACING);
        if (!canTraceOpenGl) {
            return false;
        }

        try {
            HandleViewDebug.sendStartGlTracing(this);
            return true;
        } catch (IOException e) {
            Log.w("ddms", "Start OpenGL Tracing failed");
            return false;
        }
    }

    public boolean stopOpenGlTracing() {
        boolean canTraceOpenGl = mClientData.hasFeature(ClientData.FEATURE_OPENGL_TRACING);
        if (!canTraceOpenGl) {
            return false;
        }

        try {
            HandleViewDebug.sendStopGlTracing(this);
            return true;
        } catch (IOException e) {
            Log.w("ddms", "Stop OpenGL Tracing failed");
            return false;
        }
    }

    /**
     * Sends a request to the VM to send the enable status of the method profiling. This is
     * asynchronous.
     *
     * <p>The allocation status can be accessed by {@link ClientData#getAllocationStatus()}. The
     * notification that the new status is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_HEAP_ALLOCATION_STATUS}.
     */
    public void requestMethodProfilingStatus() {
        try {
            HandleHeap.sendREAQ(this);
        } catch (IOException e) {
            Log.e("ddmlib", e);
        }
    }

    /**
     * Enables or disables the thread update.
     *
     * <p>If <code>true</code> the VM will be able to send thread information. Thread information
     * must be requested with {@link #requestThreadUpdate()}.
     *
     * @param enabled the enable flag.
     */
    public void setThreadUpdateEnabled(boolean enabled) {
        mThreadUpdateEnabled = enabled;
        if (!enabled) {
            mClientData.clearThreads();
        }

        try {
            HandleThread.sendTHEN(this, enabled);
        } catch (IOException ioe) {
            // ignore it here; client will clean up shortly
            ioe.printStackTrace();
        }

        update(CHANGE_THREAD_MODE);
    }

    /** Returns whether the thread update is enabled. */
    public boolean isThreadUpdateEnabled() {
        return mThreadUpdateEnabled;
    }

    /**
     * Sends a thread update request. This is asynchronous.
     *
     * <p>The thread info can be accessed by {@link ClientData#getThreads()}. The notification that
     * the new data is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_THREAD_DATA}.
     */
    public void requestThreadUpdate() {
        HandleThread.requestThreadUpdate(this);
    }

    /**
     * Sends a thread stack trace update request. This is asynchronous.
     *
     * <p>The thread info can be accessed by {@link ClientData#getThreads()} and {@link
     * ThreadInfo#getStackTrace()}.
     *
     * <p>The notification that the new data is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_THREAD_STACKTRACE}.
     */
    public void requestThreadStackTrace(int threadId) {
        HandleThread.requestThreadStackCallRefresh(this, threadId);
    }

    /**
     * Enables or disables the heap update.
     *
     * <p>If <code>true</code>, any GC will cause the client to send its heap information.
     *
     * <p>The heap information can be accessed by {@link ClientData#getVmHeapData()}.
     *
     * <p>The notification that the new data is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the value {@link #CHANGE_HEAP_DATA}.
     *
     * @param enabled the enable flag
     */
    public void setHeapUpdateEnabled(boolean enabled) {
        setHeapInfoUpdateEnabled(enabled);
        setHeapSegmentUpdateEnabled(enabled);
    }

    public void setHeapInfoUpdateEnabled(boolean enabled) {
        mHeapInfoUpdateEnabled = enabled;

        try {
            HandleHeap.sendHPIF(
                    this, enabled ? HandleHeap.HPIF_WHEN_EVERY_GC : HandleHeap.HPIF_WHEN_NEVER);
        } catch (IOException ioe) {
            // ignore it here; client will clean up shortly
        }

        update(CHANGE_HEAP_MODE);
    }

    public void setHeapSegmentUpdateEnabled(boolean enabled) {
        mHeapSegmentUpdateEnabled = enabled;

        try {
            HandleHeap.sendHPSG(
                    this,
                    enabled ? HandleHeap.WHEN_GC : HandleHeap.WHEN_DISABLE,
                    HandleHeap.WHAT_MERGE);
        } catch (IOException ioe) {
            // ignore it here; client will clean up shortly
        }

        update(CHANGE_HEAP_MODE);
    }

    public void initializeHeapUpdateStatus() {
        setHeapInfoUpdateEnabled(mHeapInfoUpdateEnabled);
    }

    /** Fires a single heap update. */
    public void updateHeapInfo() {
        try {
            HandleHeap.sendHPIF(this, HandleHeap.HPIF_WHEN_NOW);
        } catch (IOException ioe) {
            // ignore it here; client will clean up shortly
        }
    }

    /**
     * Returns whether any heap update is enabled.
     *
     * @see #setHeapUpdateEnabled(boolean)
     */
    public boolean isHeapUpdateEnabled() {
        return mHeapInfoUpdateEnabled || mHeapSegmentUpdateEnabled;
    }

    /**
     * Sends a native heap update request. this is asynchronous.
     *
     * <p>The native heap info can be accessed by {@link ClientData#getNativeAllocationList()}. The
     * notification that the new data is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_NATIVE_HEAP_DATA}.
     */
    public boolean requestNativeHeapInformation() {
        try {
            HandleNativeHeap.sendNHGT(this);
            return true;
        } catch (IOException e) {
            Log.e("ddmlib", e);
        }

        return false;
    }

    @Override
    public void enableAllocationTracker(boolean enable) {
        try {
            HandleHeap.sendREAE(this, enable);
        } catch (IOException e) {
            Log.e("ddmlib", e);
        }
    }

    /**
     * Sends a request to the VM to send the enable status of the allocation tracking. This is
     * asynchronous.
     *
     * <p>The allocation status can be accessed by {@link ClientData#getAllocationStatus()}. The
     * notification that the new status is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_HEAP_ALLOCATION_STATUS}.
     */
    public void requestAllocationStatus() {
        try {
            HandleHeap.sendREAQ(this);
        } catch (IOException e) {
            Log.w("ddmlib", "IO Error while obtaining allocation status");
        }
    }

    /**
     * Sends a request to the VM to send the information about all the allocations that have
     * happened since the call to {@link #enableAllocationTracker(boolean)} with <var>enable</var>
     * set to <code>null</code>. This is asynchronous.
     *
     * <p>The allocation information can be accessed by {@link ClientData#getAllocations()}. The
     * notification that the new data is available will be received through {@link
     * IClientChangeListener#clientChanged(ClientImpl, int)} with a <code>changeMask</code>
     * containing the mask {@link #CHANGE_HEAP_ALLOCATIONS}.
     */
    @Override
    public void requestAllocationDetails() {
        try {
            HandleHeap.sendREAL(this);
        } catch (IOException e) {
            Log.e("ddmlib", e);
        }
    }

    @Override
    public void kill() {
        try {
            HandleExit.sendEXIT(this, 1);
        } catch (IOException ioe) {
            Log.w("ddms", "Send of EXIT message failed");
            // ignore
        }
    }

    /** Registers the client with a Selector, should be called immediately after client creation. */
    public void register(Selector sel) throws IOException {
        // Given the only caller of this method is the thread creating this instance, we don't need
        // synchronization here.
        SocketChannel chan = mChan;
        if (chan != null) {
            chan.register(sel, SelectionKey.OP_READ, this);
        }
    }

    /**
     * Tell the client to open a server socket channel and listen for connections on the specified
     * port.
     */
    void listenForDebugger() throws IOException {
        mDebugger = new Debugger(this);
    }

    /**
     * Initiate the JDWP handshake, should be called immediately after creating client.
     *
     * <p>On failure, closes the socket and returns false.
     */
    boolean sendHandshake() {
        ByteBuffer tempBuffer = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        try {
            // assume write buffer can hold 14 bytes
            JdwpHandshake.putHandshake(tempBuffer);
            int expectedLen = tempBuffer.position();
            tempBuffer.flip();
            // synchronization on mChan not needed because it's called only once immediately after
            // object creation.
            if (mChan.write(tempBuffer) != expectedLen) {
                throw new IOException("partial handshake write");
            }
        } catch (IOException ioe) {
            Log.e("ddms-client", "IO error during handshake: " + ioe.getMessage());
            mConnState = ST_ERROR;
            close(true /* notify */);
            return false;
        }

        mConnState = ST_AWAIT_SHAKE;

        return true;
    }

    /**
     * Send a DDM packet to the client.
     *
     * <p>Ideally, we can do this with a single channel write. If that doesn't happen, we have to
     * prevent anybody else from writing to the channel until this packet completes, so we
     * synchronize on the channel.
     *
     * <p>Another goal is to avoid unnecessary buffer copies, so we write directly out of the
     * JdwpPacket's ByteBuffer.
     */
    @Override
    protected void send(@NonNull JdwpPacket packet) throws IOException {
        // Fix to avoid a race condition on mChan. This should be better synchronized
        // but just capturing the channel here, avoids a NPE.
        SocketChannel chan = mChan;
        if (chan == null) {
            // can happen for e.g. THST packets
            Log.v("ddms", "Not sending packet -- client is closed");
            return;
        }

        packet.log("Client: sending jdwp packet to Android Device");
        // Synchronizing on this variable is still useful as we do not want more than one
        // thread writing at the same time to the same channel, and the only change that
        // can happen to this channel is to be closed and mChan become null.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (chan) {
            try {
                packet.write(chan);
            } catch (IOException ioe) {
                removeReplyInterceptor(packet.getId());
                throw ioe;
            }
        }
    }

    /**
     * Read data from our channel, should only be called from one thread.
     *
     * <p>This is called when data is known to be available, and we don't yet have a full packet in
     * the buffer. If the buffer is at capacity, expand it.
     */
    public void read() throws IOException, BufferOverflowException {
        // Check the channel is not closed, and keep a copy in a local variable to prevent potential
        // NPE when we actually use the channel in the code below.
        SocketChannel chan = mChan;
        if (chan == null) {
            throw new IOException("Can't read from a closed channel.");
        }
        int count;

        if (mReadBuffer.position() == mReadBuffer.capacity()) {
            if (mReadBuffer.capacity() * 2 > mMaxPacketSize) {
                Log.e("ddms", "Exceeded MAX_BUF_SIZE!");
                throw new BufferOverflowException();
            }
            Log.d("ddms", "Expanding read buffer to " + mReadBuffer.capacity() * 2);

            ByteBuffer newBuffer = ByteBuffer.allocate(mReadBuffer.capacity() * 2);

            // copy entire buffer to new buffer
            mReadBuffer.position(0);
            newBuffer.put(mReadBuffer); // leaves "position" at end of copied

            mReadBuffer = newBuffer;
        }

        count = chan.read(mReadBuffer);
        if (count < 0) throw new IOException("read failed");

        if (Log.Config.LOGV) Log.v("ddms", "Read " + count + " bytes from " + this);
        //Log.hexDump("ddms", Log.DEBUG, mReadBuffer.array(),
        //    mReadBuffer.arrayOffset(), mReadBuffer.position());
    }

    /**
     * Return information for the first full JDWP packet in the buffer.
     *
     * <p>If we don't yet have a full packet, return null.
     *
     * <p>If we haven't yet received the JDWP handshake, we watch for it here and consume it without
     * admitting to have done so. Upon receipt we send out the "HELO" message, which is why this can
     * throw an IOException.
     *
     * <p>Note the ordering of operations on establishing a connection is:
     *
     * <p>Host side: 1) adb track-jdwp 2) Receive updated list of PIDs containing app process. 3)
     * Open/forward debugger port and connect to device. 4) Perform handshake. 5) Send HELO and wait
     * for response.
     *
     * <p>Device/process side: a) Fork zygote and update ADB with the PID. b) Send APNM if debugger
     * port is connected ("&lt;pre-initialize&gt;"). c) Bind process to actual application and
     * package. d) Send updated APNM if debugger port is connected.
     *
     * <p>The above two sequence of execution run completely in parallel, with the only constraint
     * being a) happens before 2).
     */
    public JdwpPacket getJdwpPacket() throws IOException {

        /*
         * On entry, the data starts at offset 0 and ends at "position".
         * "limit" is set to the buffer capacity.
         */
        if (mConnState == ST_AWAIT_SHAKE) {
            // Sometimes Zygote forking can race and cause the <pre-initialized>
            // APNM packet to arrive before the handshake. Just get rid of it.
            consumeInvalidPackets();

            // Normally the first response we get from the client is the response
            // to our handshake.  It doesn't look like a packet, so we have to
            // handle it specially.
            switch (JdwpHandshake.findHandshake(mReadBuffer)) {
                case JdwpHandshake.HANDSHAKE_GOOD:
                    Log.d(
                            "ddms",
                            "Good handshake from client, sending HELO to " + mClientData.getPid());
                    JdwpHandshake.consumeHandshake(mReadBuffer);
                    mConnState = ST_NEED_DDM_PKT;
                    HandleHello.sendHelloCommands(this, SERVER_PROTOCOL_VERSION);
                    // see if we have another packet in the buffer
                    return getJdwpPacket();
                case JdwpHandshake.HANDSHAKE_BAD:
                    Log.d("ddms", "Bad handshake from client");
                    if (MonitorThread.getInstance().getRetryOnBadHandshake()) {
                        // we should drop the client, but also attempt to reopen it.
                        // This is done by the DeviceMonitor.
                        mDevice.getClientTracker().trackClientToDropAndReopen(this);
                    } else {
                        // mark it as bad, close the socket, and don't retry
                        mConnState = ST_NOT_JDWP;
                        close(true /* notify */);
                    }
                    break;
                case JdwpHandshake.HANDSHAKE_NOTYET:
                    Log.d("ddms", "No handshake from client yet.");
                    break;
                default:
                    Log.e("ddms", "Unknown packet while waiting for client handshake");
            }
            return null;
        } else if (mConnState == ST_NEED_DDM_PKT
                || mConnState == ST_NOT_DDM
                || mConnState == ST_READY) {
            /*
             * Normal packet traffic.
             */
            if (mReadBuffer.position() != 0) {
                if (Log.Config.LOGV) Log.v("ddms", "Checking " + mReadBuffer.position() + " bytes");
            }
            return JdwpPacket.findPacket(mReadBuffer);
        } else {
            /*
             * Not expecting data when in this state.
             */
            Log.e("ddms", "Receiving data in state = " + mConnState);
        }

        return null;
    }

    /**
     * It's possible that APNM arrives before the handshake response. It is also invalid for any
     * packet to arrive before the handshake response. So we just discard all packets before the
     * handshake response.
     *
     * <p>Note that for APNM, we're just throwing them away prior to the handshake, since we'll get
     * that information in the HELO request/response later.
     */
    void consumeInvalidPackets() {
        while (true) {
            mReadBuffer.mark();
            try {
                JdwpPacket badPacket = JdwpPacket.findPacket(mReadBuffer);
                if (badPacket != null && !badPacket.isError() && !badPacket.isEmpty()) {
                    badPacket.consume();
                } else {
                    // We didn't find a packet, just reset the position and break out of loop.
                    mReadBuffer.reset();
                    break;
                }
            } catch (IndexOutOfBoundsException e) {
                mReadBuffer.reset();
                break;
            }
        }
    }

    /**
     * An earlier request resulted in a failure. This is the expected response to a HELO message
     * when talking to a non-DDM client.
     */
    public void packetFailed(JdwpPacket reply) {
        if (mConnState == ST_NEED_DDM_PKT) {
            Log.d("ddms", "Marking " + this + " as non-DDM client");
            mConnState = ST_NOT_DDM;
        } else if (mConnState != ST_NOT_DDM) {
            Log.w("ddms", "WEIRD: got JDWP failure packet on DDM req");
        }
    }

    /**
     * The MonitorThread calls this when it sees a DDM request or reply. If we haven't seen a DDM
     * packet before, we advance the state to ST_READY and return "false". Otherwise, just return
     * true.
     *
     * <p>The idea is to let the MonitorThread know when we first see a DDM packet, so we can send a
     * broadcast to the handlers when a client connection is made. This method is synchronized so
     * that we only send the broadcast once.
     */
    public synchronized boolean ddmSeen() {
        if (mConnState == ST_NEED_DDM_PKT) {
            mConnState = ST_READY;
            return false;
        } else if (mConnState != ST_READY) {
            Log.w("ddms", "WEIRD: in ddmSeen with state=" + mConnState);
        }
        return true;
    }

    /**
     * Close the client socket channel. If there is a debugger associated with us, close that too.
     *
     * <p>Closing a channel automatically unregisters it from the selector. However, we have to
     * iterate through the selector loop before it actually lets them go and allows the file
     * descriptors to close. The caller is expected to manage that.
     *
     * @param notify Whether or not to notify the listeners of a change.
     */
    public void close(boolean notify) {
        Log.d("ddms", "Closing " + this.toString());

        clear();
        try {
            // we could have multiple threads calling close, but it does not matter,
            // as close() is a no-op if the channel is already closed.
            SocketChannel chan = mChan;
            mChan = null;
            if (chan != null) {
                chan.close();
            }

            if (mDebugger != null) {
                mDebugger.close();
                mDebugger = null;
            }
        } catch (IOException ioe) {
            Log.w("ddms", "failed to close " + this);
            // swallow it -- not much else to do
        }

        mDevice.removeClient(this, notify);
    }

    @Override
    public boolean isValid() {
        return mChan != null;
    }

    public void update(int changeMask) {
        mDevice.update(this, changeMask);
    }

    @Override
    public void notifyVmMirrorExited() {
        mDevice.getClientTracker().trackClientToDropAndReopen(this);
    }

    @Override
    public void listViewRoots(DebugViewDumpHandler replyHandler) throws IOException {
        HandleViewDebug.listViewRoots(this, replyHandler);
    }

    @Override
    public void captureView(
            @NonNull String viewRoot, @NonNull String view, @NonNull DebugViewDumpHandler handler)
            throws IOException {
        HandleViewDebug.captureView(this, viewRoot, view, handler);
    }

    @Override
    public void dumpViewHierarchy(
            @NonNull String viewRoot,
            boolean skipChildren,
            boolean includeProperties,
            boolean useV2,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        HandleViewDebug.dumpViewHierarchy(
                this, viewRoot, skipChildren, includeProperties, useV2, handler);
    }

    @Override
    public void dumpDisplayList(@NonNull String viewRoot, @NonNull String view) throws IOException {
        HandleViewDebug.dumpDisplayList(this, viewRoot, view);
    }
}
