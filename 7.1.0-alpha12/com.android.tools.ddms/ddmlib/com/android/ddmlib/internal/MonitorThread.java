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

package com.android.ddmlib.internal;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmJdwpExtension;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.JdwpExtension;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Monitor open connections. */
public final class MonitorThread extends Thread {

    private final DdmJdwpExtension mDdmJdwpExtension;

    private volatile boolean mQuit = false;

    // List of clients we're paying attention to
    // Used for locking so final.
    private final ArrayList<ClientImpl> mClientList;

    // The almighty mux
    private Selector mSelector;

    private final List<JdwpExtension> mJdwpExtensions;

    // singleton
    private static MonitorThread sInstance;

    /**
     * Generic constructor.
     */
    private MonitorThread() {
        super("Monitor");
        mClientList = new ArrayList<>();
        mDdmJdwpExtension = new DdmJdwpExtension();
        mJdwpExtensions = new LinkedList<>();
        mJdwpExtensions.add(mDdmJdwpExtension);
    }

    /**
     * Creates and return the singleton instance of the client monitor thread.
     */
    public static MonitorThread createInstance() {
        return sInstance = new MonitorThread();
    }

    /** Get singleton instance of the client monitor thread. */
    public static MonitorThread getInstance() {
        return sInstance;
    }

    /**
     * Returns "true" if we want to retry connections to clients if we get a bad JDWP handshake
     * back, "false" if we want to just mark them as bad and leave them alone.
     */
    public boolean getRetryOnBadHandshake() {
        return true; // TODO? make configurable
    }

    /** Get an array of known clients. */
    ClientImpl[] getClients() {
        synchronized (mClientList) {
            return mClientList.toArray(new ClientImpl[0]);
        }
    }

    /** Register "handler" as the handler for type "type". */
    public synchronized void registerChunkHandler(int type, ChunkHandler handler) {
        if (sInstance == null) {
            return;
        }
        mDdmJdwpExtension.registerHandler(type, handler);
    }

    /**
     * Watch for activity from clients and debuggers.
     */
    @Override
    public void run() {
        Log.d("ddms", "Monitor is up");

        // create a selector
        try {
            mSelector = Selector.open();
        } catch (IOException ioe) {
            Log.logAndDisplay(LogLevel.ERROR, "ddms",
                    "Failed to initialize Monitor Thread: " + ioe.getMessage());
            return;
        }

        while (!mQuit) {

            try {
                /*
                 * sync with new registrations: we wait until addClient is done before going through
                 * and doing mSelector.select() again.
                 * @see {@link #addClient(Client)}
                 */
                synchronized (mClientList) {
                }

                int count;
                try {
                    count = mSelector.select();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    continue;
                } catch (CancelledKeyException cke) {
                    continue;
                }

                if (count == 0) {
                    // somebody called wakeup() ?
                    // Log.i("ddms", "selector looping");
                    continue;
                }

                Set<SelectionKey> keys = mSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    try {
                        if (key.attachment() instanceof ClientImpl) {
                            processClientActivity(key);
                        }
                        else if (key.attachment() instanceof Debugger) {
                            processDebuggerActivity(key);
                        }
                        else {
                            Log.e("ddms", "unknown activity key");
                        }
                    } catch (Exception e) {
                        // we don't want to have our thread be killed because of any uncaught
                        // exception, so we intercept all here.
                        Log.e("ddms", "Exception during activity from Selector.");
                        Log.e("ddms", e);
                    }
                }
            } catch (Exception e) {
                // we don't want to have our thread be killed because of any uncaught
                // exception, so we intercept all here.
                Log.e("ddms", "Exception MonitorThread.run()");
                Log.e("ddms", e);
            }
        }
    }

    /*
     * Something happened. Figure out what.
     */
    private void processClientActivity(SelectionKey key) {
        ClientImpl client = (ClientImpl) key.attachment();

        try {
            if (!key.isReadable() || !key.isValid()) {
                Log.d("ddms", "Invalid key from " + client + ". Dropping client.");
                dropClient(client, true /* notify */);
                return;
            }

            client.read();

            /*
             * See if we have a full packet in the buffer. It's possible we have
             * more than one packet, so we have to loop.
             */
            JdwpPacket packet = client.getJdwpPacket();
            while (packet != null) {
                packet.log("Client: received jdwp packet");
                client.incoming(packet, client.getDebugger());

                packet.consume();
                // find next
                packet = client.getJdwpPacket();
            }
        } catch (CancelledKeyException e) {
            // key was canceled probably due to a disconnected client before we could
            // read stuff coming from the client, so we drop it.
            dropClient(client, true /* notify */);
        } catch (IOException ex) {
            // something closed down, no need to print anything. The client is simply dropped.
            dropClient(client, true /* notify */);
        } catch (Exception ex) {
            Log.e("ddms", ex);

            /* close the client; automatically un-registers from selector */
            dropClient(client, true /* notify */);

            if (ex instanceof BufferOverflowException) {
                Log.w("ddms",
                        "Client data packet exceeded maximum buffer size "
                                + client);
            } else {
                // don't know what this is, display it
                Log.e("ddms", ex);
            }
        }
    }

    /**
     * Drops a client from the monitor.
     *
     * <p>This will lock the {@link ClientImpl} list of the {@link IDevice} running
     * <var>client</var>.
     *
     * @param client
     * @param notify
     */
    public synchronized void dropClient(ClientImpl client, boolean notify) {
        if (sInstance == null) {
            return;
        }

        synchronized (mClientList) {
            if (!mClientList.remove(client)) {
                return;
            }
        }
        client.close(notify);
        mDdmJdwpExtension.broadcast(DdmJdwpExtension.Event.CLIENT_DISCONNECTED, client);

        /*
         * http://forum.java.sun.com/thread.jspa?threadID=726715&start=0
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5073504
         */
        wakeup();
    }

    /**
     * Drops the provided list of clients from the monitor. This will lock the {@link ClientImpl}
     * list of the {@link IDevice} running each of the clients.
     */
    public synchronized void dropClients(Collection<? extends ClientImpl> clients, boolean notify) {
        for (ClientImpl c : clients) {
            dropClient(c, notify);
        }
    }

    /*
     * Process activity from one of the debugger sockets. This could be a new
     * connection or a data packet.
     */
    private void processDebuggerActivity(SelectionKey key) {
        Debugger dbg = (Debugger)key.attachment();

        try {
            if (key.isAcceptable()) {
                try {
                    acceptNewDebugger(dbg, null);
                } catch (IOException ioe) {
                    Log.w("ddms", "debugger accept() failed");
                    ioe.printStackTrace();
                }
            } else if (key.isReadable()) {
                processDebuggerData(key);
            } else {
                Log.d("ddm-debugger", "key in unknown state");
            }
        } catch (CancelledKeyException cke) {
            // key has been cancelled we can ignore that.
        }
    }

    /*
     * Accept a new connection from a debugger. If successful, register it with
     * the Selector.
     */
    private void acceptNewDebugger(Debugger dbg, ServerSocketChannel acceptChan)
            throws IOException {

        synchronized (mClientList) {
            SocketChannel chan;

            if (acceptChan == null)
                chan = dbg.accept();
            else
                chan = dbg.accept(acceptChan);

            if (chan != null) {
                chan.socket().setTcpNoDelay(true);
                wakeup();

                try {
                    chan.register(mSelector, SelectionKey.OP_READ, dbg);
                } catch (IOException ioe) {
                    // failed, drop the connection
                    dbg.closeData();
                    throw ioe;
                } catch (RuntimeException re) {
                    // failed, drop the connection
                    dbg.closeData();
                    throw re;
                }
            } else {
                Log.w("ddms", "ignoring duplicate debugger");
                // new connection already closed
            }
        }
    }

    /*
     * We have incoming data from the debugger. Forward it to the client.
     */
    private void processDebuggerData(SelectionKey key) {
        Debugger dbg = (Debugger)key.attachment();

        dbg.processChannelData();
    }

    /*
     * Tell the thread that something has changed.
     */
    private void wakeup() {
        // If we didn't started running yet, we might not have a selector set.
        if (mSelector != null) {
            mSelector.wakeup();
        }
    }

    /**
     * Tell the thread to stop. Called from UI thread.
     */
    public synchronized void quit() {
        mQuit = true;
        wakeup();
        Log.d("ddms", "Waiting for Monitor thread");
        try {
            this.join();
            // since we're quitting, lets drop all the client and disconnect
            // the DebugSelectedPort
            synchronized (mClientList) {
                for (ClientImpl c : mClientList) {
                    c.close(false /* notify */);
                    mDdmJdwpExtension.broadcast(DdmJdwpExtension.Event.CLIENT_DISCONNECTED, c);
                }
                mClientList.clear();
            }

            if (mSelector != null) {
                mSelector.close();
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        sInstance = null;
    }

    /**
     * Add a new Client to the list of things we monitor. Also adds the client's channel and the
     * client's debugger listener to the selection list. This should only be called from one thread
     * (the VMWatcherThread) to avoid a race between "alreadyOpen" and Client creation.
     */
    public synchronized void addClient(ClientImpl client) {
        if (sInstance == null) {
            return;
        }

        Log.d("ddms", "Adding new client " + client);

        synchronized (mClientList) {
            mClientList.add(client);

            for (JdwpExtension extension : mJdwpExtensions) {
                extension.intercept(client);
            }

            /*
             * Register the Client's socket channel with the selector. We attach
             * the Client to the SelectionKey. If you try to register a new
             * channel with the Selector while it is waiting for I/O, you will
             * block. The solution is to call wakeup() and then hold a lock to
             * ensure that the registration happens before the Selector goes
             * back to sleep.
             */
            try {
                wakeup();

                client.register(mSelector);

                Debugger dbg = client.getDebugger();
                if (dbg != null) {
                    dbg.registerListener(mSelector);
                }
            } catch (IOException ioe) {
                // not really expecting this to happen
                ioe.printStackTrace();
            }
        }
    }

    public DdmJdwpExtension getDdmExtension() {
        return mDdmJdwpExtension;
    }
}
