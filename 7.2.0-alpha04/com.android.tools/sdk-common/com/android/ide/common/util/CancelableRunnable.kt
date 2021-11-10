/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.util

import com.android.annotations.concurrency.GuardedBy

/**
 * A [Runnable] that can be cancelled while it is running or before it gets a chance to run.
 */
class CancelableRunnable(private val myRunnable: Runnable) : Runnable {
    @GuardedBy("myLock")
    private var canceled: Boolean = false
    @GuardedBy("myLock")
    private val activeThreads = HashSet<Thread>()
    private val lock = Any()

    /**
     * Attempts to cancel execution of this runnable. If this runnable has not started when [cancel] is called,
     * this runnable will never run. If the runnable has already started, then the [mayInterruptIfRunning]
     * parameter determines whether the thread executing this runnable should be interrupted in an attempt to
     * stop the runnable. If the runnable has already finished running, then this call has no effect on that
     * execution but will prevent the runnable from executing again.
     *
     * @param mayInterruptIfRunning `true` if the thread executing this runnable should be interrupted;
     * otherwise, an in-progress runnable is allowed to complete
     * @return `true` if the runnable was canceled, `false` if the runnable has ben canceled already
     */
    fun cancel(mayInterruptIfRunning: Boolean) {
        synchronized(lock) {
            if (!canceled) {
                canceled = true
                if (mayInterruptIfRunning) {
                    activeThreads.forEach(Thread::interrupt)
                }
            }
        }
    }

    /**
     * Returns `true` if the [cancel] method was called for this runnable.
     */
    val isCancelled: Boolean
        get() = synchronized(lock) {
            return canceled
        }

    override fun run() {
        synchronized(lock) {
            if (canceled) {
                return
            }
            activeThreads.add(Thread.currentThread())
        }

        try {
            myRunnable.run()
        } finally {
            synchronized(lock) {
                activeThreads.remove(Thread.currentThread())
                Thread.interrupted() // Clear interrupted status of the thread just in case.
            }
        }
    }
}
