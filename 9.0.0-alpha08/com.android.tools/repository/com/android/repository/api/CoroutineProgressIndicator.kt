/*
 * Copyright (C) 2025 The Android Open Source Project
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

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.job

/** Decorates a ProgressIndicator, tying its cancellation to that of the given coroutine context. */
class CoroutineProgressIndicator(
  val coroutineContext: CoroutineContext,
  delegate: ProgressIndicator,
) : DelegatingProgressIndicator(delegate) {
  override fun isCanceled(): Boolean = coroutineContext.job.isCancelled

  override fun cancel() {
    coroutineContext.job.cancel()
  }
}
