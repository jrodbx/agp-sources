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
package com.android.repository.api

/**
 * An interface for facilities that can run tasks, synchronously or asynchronously, and allow them to show their progress using a
 * [ProgressIndicator].
 */
interface ProgressRunner {
  /** Runs a task asynchronously. */
  fun runAsyncWithProgress(r: ProgressRunnable)

  /** Runs a task synchronously. */
  fun runSyncWithProgress(r: ProgressRunnable)

  /** Interface for tasks that can show their progress using a [ProgressIndicator]. */
  fun interface ProgressRunnable {
    suspend fun run(indicator: ProgressIndicator)
  }
}
