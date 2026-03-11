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

import com.android.ddmlib.TimeoutException;
import java.io.IOException;

/**
 * Interface to make managing reading / shutting down sockets {@link JdwpProxyClient} and
 * {@link JdwpClientManager} easier. Objects of this type are registered with the selector
 * apart of {@link JdwpProxyServer}
 */
interface JdwpSocketHandler {
    /**
     * Read data from internal socket
     */
    void read() throws IOException, TimeoutException;

    /**
     * Shutdown connection with internal socket.
     */
    void shutdown() throws IOException;
}
