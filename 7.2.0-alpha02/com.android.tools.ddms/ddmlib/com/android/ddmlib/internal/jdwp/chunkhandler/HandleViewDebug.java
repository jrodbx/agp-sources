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

package com.android.ddmlib.internal.jdwp.chunkhandler;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.ByteBufferUtil;
import com.android.ddmlib.Client;
import com.android.ddmlib.DebugViewDumpHandler;
import com.android.ddmlib.Log;
import com.android.ddmlib.internal.MonitorThread;
import com.android.ddmlib.internal.ClientImpl;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class HandleViewDebug extends ChunkHandler {
    /** Dump view hierarchy. */
    private static final int VURT_DUMP_HIERARCHY = 1;

    /** Capture View Layers. */
    private static final int VURT_CAPTURE_LAYERS = 2;

    /** Dump View Theme. */
    private static final int VURT_DUMP_THEME = 3;

    /** Capture View. */
    private static final int VUOP_CAPTURE_VIEW = 1;

    /** Obtain the Display List corresponding to the view. */
    private static final int VUOP_DUMP_DISPLAYLIST = 2;

    /** Profile a view. */
    private static final int VUOP_PROFILE_VIEW = 3;

    /** Invoke a method on the view. */
    private static final int VUOP_INVOKE_VIEW_METHOD = 4;

    /** Set layout parameter. */
    private static final int VUOP_SET_LAYOUT_PARAMETER = 5;

    private static final String TAG = "ddmlib"; //$NON-NLS-1$

    private static final HandleViewDebug sInstance = new HandleViewDebug();

    private static final DebugViewDumpHandler sViewOpNullChunkHandler =
            new NullChunkHandler(DebugViewDumpHandler.CHUNK_VUOP);

    private HandleViewDebug() {}

    public static void register(MonitorThread mt) {
        // TODO: add chunk type for auto window updates
        // and register here
        mt.registerChunkHandler(DebugViewDumpHandler.CHUNK_VUGL, sInstance);
        mt.registerChunkHandler(DebugViewDumpHandler.CHUNK_VULW, sInstance);
        mt.registerChunkHandler(DebugViewDumpHandler.CHUNK_VUOP, sInstance);
        mt.registerChunkHandler(DebugViewDumpHandler.CHUNK_VURT, sInstance);
    }

    @Override
    public void clientReady(ClientImpl client) throws IOException {}

    @Override
    public void clientDisconnected(ClientImpl client) {}

    public static void listViewRoots(Client client, DebugViewDumpHandler replyHandler)
            throws IOException {
        ByteBuffer buf = allocBuffer(8);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(1);
        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VULW, chunkBuf.position());
        ((ClientImpl) client).send(packet, replyHandler);
    }

    public static void dumpViewHierarchy(
            @NonNull Client client,
            @NonNull String viewRoot,
            boolean skipChildren,
            boolean includeProperties,
            boolean useV2,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        ByteBuffer buf =
                allocBuffer(
                        4 // opcode
                                + 4 // view root length
                                + viewRoot.length() * 2 // view root
                                + 4 // skip children
                                + 4 // include view properties
                                + 4); // use Version 2
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(VURT_DUMP_HIERARCHY);
        chunkBuf.putInt(viewRoot.length());
        ByteBufferUtil.putString(chunkBuf, viewRoot);
        chunkBuf.putInt(skipChildren ? 1 : 0);
        chunkBuf.putInt(includeProperties ? 1 : 0);
        chunkBuf.putInt(useV2 ? 1 : 0);

        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VURT, chunkBuf.position());
        ((ClientImpl) client).send(packet, handler);
    }

    public static void captureLayers(
            @NonNull ClientImpl client,
            @NonNull String viewRoot,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        int bufLen = 8 + viewRoot.length() * 2;

        ByteBuffer buf = allocBuffer(bufLen);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(VURT_CAPTURE_LAYERS);
        chunkBuf.putInt(viewRoot.length());
        ByteBufferUtil.putString(chunkBuf, viewRoot);

        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VURT, chunkBuf.position());
        client.send(packet, handler);
    }

    private static void sendViewOpPacket(
            @NonNull Client client,
            int op,
            @NonNull String viewRoot,
            @NonNull String view,
            @Nullable byte[] extra,
            @Nullable DebugViewDumpHandler handler)
            throws IOException {
        int bufLen = 4 +                        // opcode
                4 + viewRoot.length() * 2 +     // view root strlen + view root
                4 + view.length() * 2;          // view strlen + view

        if (extra != null) {
            bufLen += extra.length;
        }

        ByteBuffer buf = allocBuffer(bufLen);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(op);
        chunkBuf.putInt(viewRoot.length());
        ByteBufferUtil.putString(chunkBuf, viewRoot);

        chunkBuf.putInt(view.length());
        ByteBufferUtil.putString(chunkBuf, view);

        if (extra != null) {
            chunkBuf.put(extra);
        }

        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VUOP, chunkBuf.position());
        ((ClientImpl) client).send(packet, handler);
    }

    public static void profileView(
            @NonNull ClientImpl client,
            @NonNull String viewRoot,
            @NonNull String view,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        sendViewOpPacket(client, VUOP_PROFILE_VIEW, viewRoot, view, null, handler);
    }

    public static void captureView(
            @NonNull Client client,
            @NonNull String viewRoot,
            @NonNull String view,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        sendViewOpPacket(client, VUOP_CAPTURE_VIEW, viewRoot, view, null, handler);
    }

    public static void invalidateView(
            @NonNull ClientImpl client, @NonNull String viewRoot, @NonNull String view)
            throws IOException {
        invokeMethod(client, viewRoot, view, "invalidate");
    }

    public static void requestLayout(
            @NonNull ClientImpl client, @NonNull String viewRoot, @NonNull String view)
            throws IOException {
        invokeMethod(client, viewRoot, view, "requestLayout");
    }

    public static void dumpDisplayList(
            @NonNull Client client, @NonNull String viewRoot, @NonNull String view)
            throws IOException {
        sendViewOpPacket(
                client, VUOP_DUMP_DISPLAYLIST, viewRoot, view, null, sViewOpNullChunkHandler);
    }

    public static void dumpTheme(
            @NonNull ClientImpl client,
            @NonNull String viewRoot,
            @NonNull DebugViewDumpHandler handler)
            throws IOException {
        ByteBuffer buf =
                allocBuffer(
                        4 // opcode
                                + 4 // view root length
                                + viewRoot.length() * 2); // view root
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(VURT_DUMP_THEME);
        chunkBuf.putInt(viewRoot.length());
        ByteBufferUtil.putString(chunkBuf, viewRoot);

        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VURT, chunkBuf.position());
        client.send(packet, handler);
    }

    /** A {@link ViewDumpHandler} to use when no response is expected. */
    private static class NullChunkHandler extends DebugViewDumpHandler {
        public NullChunkHandler(int chunkType) {
            super(chunkType);
        }

        @Override
        protected void handleViewDebugResult(ByteBuffer data) {
        }
    }

    public static void invokeMethod(
            @NonNull ClientImpl client,
            @NonNull String viewRoot,
            @NonNull String view,
            @NonNull String method,
            Object... args)
            throws IOException {
        int len = 4 + method.length() * 2;
        if (args != null) {
            // # of args
            len += 4;

            // for each argument, we send a char type specifier (2 bytes) and
            // the arg value (max primitive size = sizeof(double) = 8
            len += 10 * args.length;
        }

        byte[] extra = new byte[len];
        ByteBuffer b = ByteBuffer.wrap(extra);

        b.putInt(method.length());
        ByteBufferUtil.putString(b, method);

        if (args != null) {
            b.putInt(args.length);

            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Boolean) {
                    b.putChar('Z');
                    b.put((byte) ((Boolean) arg ? 1 : 0));
                } else if (arg instanceof Byte) {
                    b.putChar('B');
                    b.put((Byte) arg);
                } else if (arg instanceof Character) {
                    b.putChar('C');
                    b.putChar((Character) arg);
                } else if (arg instanceof Short) {
                    b.putChar('S');
                    b.putShort((Short) arg);
                } else if (arg instanceof Integer) {
                    b.putChar('I');
                    b.putInt((Integer) arg);
                } else if (arg instanceof Long) {
                    b.putChar('J');
                    b.putLong((Long) arg);
                } else if (arg instanceof Float) {
                    b.putChar('F');
                    b.putFloat((Float) arg);
                } else if (arg instanceof Double) {
                    b.putChar('D');
                    b.putDouble((Double) arg);
                } else {
                    Log.e(TAG, "View method invocation only supports primitive arguments, supplied: " + arg);
                    return;
                }
            }
        }

        sendViewOpPacket(
                client, VUOP_INVOKE_VIEW_METHOD, viewRoot, view, extra, sViewOpNullChunkHandler);
    }

    public static void setLayoutParameter(
            @NonNull ClientImpl client,
            @NonNull String viewRoot,
            @NonNull String view,
            @NonNull String parameter,
            int value)
            throws IOException {
        int len = 4 + parameter.length() * 2 + 4;
        byte[] extra = new byte[len];
        ByteBuffer b = ByteBuffer.wrap(extra);

        b.putInt(parameter.length());
        ByteBufferUtil.putString(b, parameter);
        b.putInt(value);
        sendViewOpPacket(
                client, VUOP_SET_LAYOUT_PARAMETER, viewRoot, view, extra, sViewOpNullChunkHandler);
    }

    @Override
    public void handleChunk(
            ClientImpl client, int type, ByteBuffer data, boolean isReply, int msgId) {}

    public static void sendStartGlTracing(ClientImpl client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(1);
        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VUGL, chunkBuf.position());

        client.send(packet, null);
    }

    public static void sendStopGlTracing(ClientImpl client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(0);
        finishChunkPacket(packet, DebugViewDumpHandler.CHUNK_VUGL, chunkBuf.position());

        client.send(packet, null);
    }
}

