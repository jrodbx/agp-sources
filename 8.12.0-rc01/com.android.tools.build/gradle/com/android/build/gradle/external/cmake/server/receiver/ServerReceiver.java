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

package com.android.build.gradle.external.cmake.server.receiver;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Receiver that receives general messages produced by Cmake server when it is processing a request.
 */
public class ServerReceiver {
    private ProgressReceiver progressReceiver = null;
    private MessageReceiver messageReceiver = null;
    private SignalReceiver signalReceiver = null;
    private DiagnosticReceiver diagnosticReceiver = null;
    private DeserializationMonitor deserializationMonitor = null;

    @Nullable
    public ProgressReceiver getProgressReceiver() {
        return progressReceiver;
    }

    public ServerReceiver setProgressReceiver(@NonNull ProgressReceiver progressReceiver) {
        this.progressReceiver = progressReceiver;
        return this;
    }

    @Nullable
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public ServerReceiver setMessageReceiver(@NonNull MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
        return this;
    }

    @Nullable
    public SignalReceiver getSignalReceiver() {
        return signalReceiver;
    }

    public ServerReceiver setSignalReceiver(@NonNull SignalReceiver signalReceiver) {
        this.signalReceiver = signalReceiver;
        return this;
    }

    @Nullable
    public DiagnosticReceiver getDiagnosticReceiver() {
        return diagnosticReceiver;
    }

    public ServerReceiver setDiagnosticReceiver(@NonNull DiagnosticReceiver diagnosticReceiver) {
        this.diagnosticReceiver = diagnosticReceiver;
        return this;
    }

    @Nullable
    public DeserializationMonitor getDeserializationMonitor() {
        return deserializationMonitor;
    }

    public ServerReceiver setDeserializationMonitor(
            @NonNull DeserializationMonitor deserializationMonitor) {
        this.deserializationMonitor = deserializationMonitor;
        return this;
    }
}
