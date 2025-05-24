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

package com.android.ddmlib.internal.jdwp.interceptor;

import com.android.annotations.NonNull;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.IOException;

/**
 * Interface that allows for custom intercepting of data to and from the device.
 * All interceptors are run even if one returns true to filter the data. The data will still be
 * filtered however other interceptors may need to update internal state depending on the type
 * of data. Since there is no way of knowing each interceptor is run.
 * <p>
 * When implementing an interceptor care should be taken to only act on the packets / data specific
 * to the purpose of that interceptor.
 * <p>
 * It is okay for interceptors to write data directly to
 * clients both when filtering data to the device, and when filtering data from clients.
 * <p>
 * No interceptor should modify the data inplace for either the packets or the byte[]. Both of these
 * objects are backed by a single buffer and should be treated as transient readonly memory. This
 * means if an interceptor wants to cache data received a copy of hte buffer or packet should be
 * made.
 */
public interface Interceptor {
    /**
     * So each Interceptor doesn't need to manage parsing {@link JdwpPacket}'s two helper functions
     * are provided that allow filters to intercept and inspect packets that are about to be sent.
     * This method allows interceptors to filter packets that are going to the device.
     *
     * @param from         which client is sending the data
     * @param packetToSend if a {@link JdwpPacket} could be parsed from the data. This is the packet
     *                     that will be sent to the device.
     * @return true if data should be filtered from being sent to device false otherwise
     * @throws IOException      is thrown if {@link JdwpProxyClient ::write} fails.
     * @throws TimeoutException is thrown if writing times out.
     */
    default boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull JdwpPacket packetToSend) throws IOException, TimeoutException {
        return false;
    }

    /**
     * So each Interceptor doesn't need to manage parsing {@link JdwpPacket}'s two helper functions
     * are provided that allow filters to intercept and inspect packets that are about to be sent.
     * This method allows interceptors to filter packets that are going to the "to" client.
     *
     * @param to           which client is sending the data
     * @param packetToSend if a {@link JdwpPacket} could be parsed from the data. This is the packet
     *                     that will be sent to the device.
     * @return true if data should be filtered from being sent to client false otherwise
     * @throws IOException      is thrown if {@link JdwpProxyClient ::write} fails.
     * @throws TimeoutException is thrown if writing times out.
     */
    default boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packetToSend) throws IOException, TimeoutException {
        return false;
    }
}
