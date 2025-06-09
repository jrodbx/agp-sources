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
package com.android.instantapp.run;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Listener that can be implemented by Gradle or the IDE to receive progress on running process. */
public interface RunListener {

    /**
     * Method will be called for general information on the progress.
     *
     * @param message the received message.
     */
    default void printMessage(@NonNull String message) {}

    /**
     * Method will be called for general information, warnings and errors.
     *
     * @param message the received message.
     * @param e a possible exception.
     */
    default void logMessage(@NonNull String message, @Nullable InstantAppRunException e) {}

    /**
     * Method will be called with partial progress representation.
     *
     * @param fraction estimated fraction of the process already run.
     */
    default void setProgress(double fraction) {}

    /**
     * Indicates if user cancelled the process.
     *
     * @return {@code true} if it's been cancelled.
     */
    default boolean isCancelled() {
        return false;
    }

    class NullListener implements RunListener {}
}
