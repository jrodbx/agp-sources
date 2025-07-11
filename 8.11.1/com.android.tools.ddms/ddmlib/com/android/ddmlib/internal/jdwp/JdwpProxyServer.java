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
package com.android.ddmlib.internal.jdwp;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.Log;
import com.android.ddmlib.TimeoutException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * A proxy server that injects itself between {@link com.android.ddmlib.internal.ClientImpl} and
 * adb. The proxy servers job is to multi-plex ddmlib connects and manage both client and device
 * state. This allows us to run more than one instance of ddmlib with the "clientSupported" flag
 * enabled.
 * <p>
 * This server is fault tolerant meaning if multiple ddmlib clients are running and the one
 * designated as the server shutdowns this server will detect the termination of the server and
 * attempt to restart itself in server mode. These two modes are called server mode and fallback
 * mode. When a server changes state from fallback to server a callback is made informing clients
 * that they need to refresh their device list and client list. This is because all the state
 * managed by the previous server was discarded and dropping and reconnecting devices / clients
 * allows the new server to build its own internal state and resync with adb / jdwp.
 * <p>
 * It should be noted that while this allows multiplexing most data it does not allow multiplexing
 * of the debugger. If a debugger is attached all non-cached request from other clients will be
 * discarded. Request are frequently cached by {@link com.android.ddmlib.internal.jdwp.interceptor.Interceptor}
 * managed by a clients {@link JdwpClientManager}
 */
public class JdwpProxyServer implements Runnable {
    private static final long THROTTLE_TIMEOUT_MS = 1000;
    private static final long JOIN_TIMEOUT_MS = 5000;
    /**
     * Callback for when a server drops and a new server has taken its place.
     */
    public interface ConnectionState {
        void changed();
    }

    private final int mListenPort;
    private final ConnectionState mConnectionStateChangedCallback;
    /**
     * The sockets can be accessed on the JdwpProxyConnection thread or the main thread. The main
     * thread is responsible for shutting down the server. If the server is shutdown from the main
     * thread while transitioning state this can cause a variety of timing issues.
     */
    private final Object myChannelLock = new Object();

    @GuardedBy("myChannelLock")
    private ServerSocketChannel mListenChannel;

    @GuardedBy("myChannelLock")
    private SocketChannel mFallbackChannel;

    private boolean mQuit = false;
    private Selector mSelector;
    private JdwpClientManagerFactory mFactory;
    private boolean mIsRunningAsServer = false;
    private InetSocketAddress mServerAddress;
    private long mLastAttemptTime;
    private Thread myRunThread;

    public JdwpProxyServer(int listenPort, @NonNull ConnectionState callback) {
        mListenPort = listenPort;
        mConnectionStateChangedCallback = callback;
    }

    public void start() throws IOException {
        mServerAddress = new InetSocketAddress(
          InetAddress.getByName("localhost"), //$NON-NLS-1$
          mListenPort);
        try {
            startAsServer();
        }
        catch (BindException ex) {
            // A server is already running, connect to that server and if it dies retry at starting as a server.
            startAsClient();
        }
        myRunThread = new Thread(this, "JdwpProxyConnection");
        myRunThread.start();
    }

    @VisibleForTesting
    boolean IsRunningAsServer() {
        return mIsRunningAsServer;
    }

    @VisibleForTesting
    boolean IsConnectedOrListening() {
        synchronized (myChannelLock) {
            return (mListenChannel != null && mListenChannel.socket().isBound())
                    || (mFallbackChannel != null && mFallbackChannel.isConnected());
        }
    }

    @VisibleForTesting
    JdwpClientManagerFactory getFactory() {
        return mFactory;
    }

    private void startAsServer() throws IOException {
        synchronized (myChannelLock) {
            mListenChannel = ServerSocketChannel.open();
            mSelector = Selector.open();
            mFactory = new JdwpClientManagerFactory(mSelector);
            mListenChannel.socket().setReuseAddress(true); // enable SO_REUSEADDR
            mListenChannel.socket().bind(mServerAddress);
            mListenChannel.configureBlocking(false);
            mListenChannel.register(mSelector, SelectionKey.OP_ACCEPT, this);
            mIsRunningAsServer = true;
        }
    }

    @VisibleForTesting
    int getBindPort() {
        synchronized (myChannelLock) {
            assert mListenChannel != null;
            return mListenChannel.socket().getLocalPort();
        }
    }

    private void startAsClient() {
        mIsRunningAsServer = false;
    }

    public void stop() {
        mQuit = true;
        if (mSelector != null) {
            mSelector.wakeup();
        }
        synchronized (myChannelLock) {
            if (mFallbackChannel != null) {
                try {
                    mFallbackChannel.close();
                } catch (IOException ex) {
                    // Failed to close client socket
                }
            }
        }
        // Shutdown the sockets first, so that they will stop reading packets, potentially
        // causing the join to hang (which will freeze the UI).
        try {
            // Close any open child sockets.
            if (mSelector != null) {
                if (!mSelector.keys().isEmpty()) {
                    Iterator<SelectionKey> keys = mSelector.keys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        if (key.attachment() instanceof JdwpSocketHandler) {
                            ((JdwpSocketHandler)key.attachment()).shutdown();
                        }
                    }
                }
                mSelector.close();
            }
        }
        catch (IOException ex) {
            // Failed to close selector
        }
        // Wait until our run thread exits. This guarantees we can cleanup all data used by this
        // this thread without risk of threading issues.
        if (myRunThread != null) {
            try {
                myRunThread.join(JOIN_TIMEOUT_MS);
                if (myRunThread.isAlive()) {
                    Log.e("ddms", "Run thread still alive after " + JOIN_TIMEOUT_MS + "ms");
                }
            } catch (InterruptedException ex) {
                // Failed to wait for thread to stop.
            }
        }
        synchronized (myChannelLock) {
            if (mListenChannel != null) {
                try {
                    mListenChannel.close();
                    mListenChannel.socket().close();
                } catch (IOException ex) {
                    // Failed to close server socket.
                }
            }
        }

        mSelector = null;
        synchronized (myChannelLock) {
            mListenChannel = null;
            mFallbackChannel = null;
        }
        myRunThread = null;
    }

    private void runAsFallbackServer() throws IOException, InterruptedException {
        try {
            // In case we get into a raise condition where we cannot start a client or a server, throttle the thread to try
            // again only at a throttled limit.
            if (System.currentTimeMillis() - mLastAttemptTime < THROTTLE_TIMEOUT_MS) {
                Thread.sleep(THROTTLE_TIMEOUT_MS);
            }
            mLastAttemptTime = System.currentTimeMillis();
            SocketChannel chan;
            synchronized (myChannelLock) {
                if (mQuit) {
                    return;
                }
                if (mFallbackChannel == null) {
                    mFallbackChannel = SocketChannel.open(mServerAddress);
                }
                chan = mFallbackChannel;
            }
            ByteBuffer buffer = ByteBuffer.allocate(1);
            // If we are able to open a socket attempt to read from the channel. Our server never
            // writes data to clients that haven't
            // initialized themselves as such this read will block the thread until the server dies.
            chan.read(buffer);
            retryAsServer();
        }
        catch (IOException ex) {
            retryAsServer();
        }
    }

    private void retryAsServer() throws IOException {
        if (mQuit) {
            return;
        }
        // If we fail to connect or our connection is interrupted, maybe the server died attempt to
        // start as a server.
        synchronized (myChannelLock) {
            if (mFallbackChannel != null) {
                mFallbackChannel.close();
                mFallbackChannel = null;
            }
        }
        if (mSelector != null) {
            mSelector.close();
            mSelector = null;
        }
        if (!mQuit) {
            startAsServer();
            mConnectionStateChangedCallback.changed();
        }
    }

    private void runAsServer() throws IOException {
        int count = mSelector.select();
        if (count == 0) {
            // somebody called wakeup() ?
            // Log.i("ddms", "selector looping");
            return;
        }
        Set<SelectionKey> keys = mSelector.selectedKeys();
        Iterator<SelectionKey> iter = keys.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();
            try {
                if (key.isAcceptable()) {
                    synchronized (myChannelLock) {
                        SocketChannel client = mListenChannel.accept();
                        client.configureBlocking(false);
                        client.register(
                                mSelector,
                                SelectionKey.OP_READ,
                                new JdwpProxyClient(client, mFactory));
                    }
                }
                else if (key.attachment() instanceof JdwpSocketHandler) {
                    JdwpSocketHandler handler = (JdwpSocketHandler)key.attachment();
                    try {
                        handler.read();
                    } catch (TimeoutException | IOException | BufferOverflowException ex) {
                        // BufferOverflowExceptions are thrown when the proxy fails to parse a
                        // jdwp packet properly, or attempts to parse a packet larger than the
                        // maximum supported size. When this happens the proxy mirrors the behavior
                        // of studio and will shutdown the client app.
                        handler.shutdown();
                    }
                }
                else {
                    Log.e("ddms", "unknown activity key");
                }
            }
            catch (Exception e) {
                // we don't want to have our thread be killed because of any uncaught
                // exception, so we intercept all here.
                Log.e("ddms", "Exception during activity from Selector.");
                Log.e("ddms", e);
            }
        }
    }

    @Override
    public void run() {
        while (!mQuit) {
            try {
                if (mIsRunningAsServer) {
                    runAsServer();
                }
                else {
                    runAsFallbackServer();
                }
            }
            catch (Exception ex) {
                Log.e("JdwpProxyServer", ex);
            }
        }
    }
}
