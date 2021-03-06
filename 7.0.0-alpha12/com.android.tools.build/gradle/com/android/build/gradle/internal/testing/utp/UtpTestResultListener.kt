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

package com.android.build.gradle.internal.testing.utp

import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent

/**
 * An interface to listen streaming test results from UTP.
 */
interface UtpTestResultListener {

    /**
     * Called when a new test result event is available.
     */
    fun onTestResultEvent(testResultEvent: TestResultEvent)

    /**
     * Called when an error happens in AGP/UTP communication. If this method is invoked,
     * it's the last method call and no more [onTestResultEvent] or [onCompleted] are
     * invoked.
     */
    fun onError()

    /**
     * Called when all test result events are received successfully.
     */
    fun onCompleted()
}
