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
import java.util.concurrent.CancellationException;

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

    /**
     * Rethrows the given exception if it means that the current computation was cancelled. This
     * method is intended to be used in the following context:
     *
     * <pre>
     *     try {
     *         // Code that calls ProgressManagerAdapter.checkCancelled()
     *     }
     *     catch (Exception e) {
     *         ProgressManagerAdapter.throwIfCancellation(e);
     *         // Handle other exceptions.
     *     }
     * </pre>
     */
    public static void throwIfCancellation(@NonNull Throwable t) {
        ProgressManagerAdapter instance = ourInstance;
        if (instance == null) {
            throwIfCancellationException(t);
        } else {
            instance.doThrowIfCancellation(t);
        }
    }

    protected abstract void doCheckCanceled();

    protected void doThrowIfCancellation(@NonNull Throwable t) {
        throwIfCancellationException(t);
    }

    private static void throwIfCancellationException(@NonNull Throwable t) {
        if (t instanceof CancellationException) {
            throw (CancellationException) t;
        }
    }

    protected static void setInstance(@NonNull ProgressManagerAdapter instance) {
        Preconditions.checkState(ourInstance == null);
        ourInstance = instance;
    }
}
