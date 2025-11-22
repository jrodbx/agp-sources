/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.Callable
import java.util.concurrent.Semaphore

/**
 * Class to limit concurrent heavyweight tasks.
 */
class WorkLimiter internal constructor(concurrencyLimit: Int) {

    private val semaphore: Semaphore = Semaphore(concurrencyLimit, true)

    /**
     * Run the given callable, blocking in a fair way if needed to keep the number of concurrent
     * tasks down to the `concurrencyLimit` passed in the constructor.
     */
    @Throws(InterruptedException::class)
    fun limit(task: Callable<Void>) {
        semaphore.acquire()
        try {
            task.call()
        } finally {
            semaphore.release()
        }
    }

}
