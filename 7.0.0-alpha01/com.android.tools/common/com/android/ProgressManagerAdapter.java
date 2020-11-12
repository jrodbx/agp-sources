/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;

/**
 * An adapter for accessing environment-dependent progress and cancellation functionality. By
 * default all public methods of this class have no effect. See subclasses for descriptions of
 * behavior in specific environments.
 */
public abstract class ProgressManagerAdapter {
    private static ProgressManagerAdapter ourInstance;

    /**
     * Checks if the progress indicator associated with the current thread has been canceled and, if
     * so, throws an unchecked exception. The exact type of the exception is environment-dependent.
     */
    public static void checkCanceled() {
        ProgressManagerAdapter instance = ourInstance;
        if (instance != null) {
            instance.doCheckCanceled();
        }
    }

    protected abstract void doCheckCanceled();

    protected static void setInstance(@NonNull ProgressManagerAdapter instance) {
        Preconditions.checkState(ourInstance == null);
        ourInstance = instance;
    }
}
