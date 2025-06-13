/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.instantapp.utils;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Service listening logcat outputs, parsing it and retransmitting to the registered listeners. */
public class LogcatService {
    @NonNull private final IDevice myDevice;
    @NonNull private final LogCatReceiverTask myLogCatReceiverTask;

    public LogcatService(@NonNull IDevice device) {
        myDevice = device;
        myLogCatReceiverTask = new LogCatReceiverTask(device);
    }

    public interface Listener {
        void onLogLineReceived(@NonNull LogCatMessage line);
    }

    public void startListening(@NonNull Listener listener) {
        ThreadFactory factory =
                new ThreadFactoryBuilder().setNameFormat("logcat-" + myDevice.getName()).build();
        Executor executor = Executors.newSingleThreadExecutor(factory);
        myLogCatReceiverTask.addLogCatListener(
                msgList -> {
                    for (LogCatMessage message : msgList) {
                        listener.onLogLineReceived(message);
                    }
                });
        executor.execute(myLogCatReceiverTask);
    }

    public void stopListening() {
        myLogCatReceiverTask.stop();
    }
}
