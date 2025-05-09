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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.TimeoutException;
import java.io.IOException;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple factory that returns a new or existing {@link JdwpClientManager} for a given {@link
 * JdwpClientManagerId}.
 */
public class JdwpClientManagerFactory {

    private Map<JdwpClientManagerId, JdwpClientManager> myConnections = new HashMap<>();
    Selector selector;

    public JdwpClientManagerFactory(Selector selector) {
        this.selector = selector;
    }

    public JdwpClientManager getConnection(String deviceId, int pid) {
        return myConnections.getOrDefault(new JdwpClientManagerId(deviceId, pid), null);
    }

    public JdwpClientManager createConnection(JdwpClientManagerId id)
            throws AdbCommandRejectedException, TimeoutException, IOException {
        JdwpClientManager connection = myConnections.get(id);
        if (connection == null) {
            connection = new JdwpClientManager(id, selector);
            connection.addShutdownListener(() -> myConnections.remove(id));
            myConnections.put(id, connection);
        }
        return connection;
    }
}
