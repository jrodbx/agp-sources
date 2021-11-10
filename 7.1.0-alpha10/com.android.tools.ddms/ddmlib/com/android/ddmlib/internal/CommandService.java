/*
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * Class to accept external connections and issue commands on a running ddmlib service.
 * The format of the incoming data is expected to match the format of adb commands
 * String formatted as follows: [Size(in hex 4 chars)][command]:[args]
 * Example: 001Cdisconnect:device-id:1234
 */
class CommandService implements Runnable {
    CommandService(int port) {

    }

    void start() {
        // TODO (194901500): Start server socket and listen for incoming connections.
        // TODO (194901500): Setup async selector and start service thread.
    }

    @Override
    public void run() {
        // TODO (194901500): Accept incoming connections, connections are kept alive.
        // TODO (194901500): Handle commands, Commands will be defined by ICommand.
    }
}
