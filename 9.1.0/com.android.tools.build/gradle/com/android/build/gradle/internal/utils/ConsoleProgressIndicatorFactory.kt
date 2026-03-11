/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.repository.api.ConsoleProgressIndicator
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Creates [ConsoleProgressIndicator] instances with appropriate terminal capability param, by safely reading it from system variables,
 * without affecting Gradle configuration cache.
 *
 * In AGP when need to create [ConsoleProgressIndicator], use this factory instead. More info: b/379657438
 */
class ConsoleProgressIndicatorFactory(private val providerFactory: ProviderFactory) {

  @Suppress("AvoidByLazy")
  private val isTerminalSmart: Boolean by lazy { providerFactory.of(IsTerminalSmartValueSource::class.java) {}.get() }

  fun create(): ConsoleProgressIndicator = ConsoleProgressIndicator(canPrintProgress = isTerminalSmart)

  /** @param prefix use this string as a prefix for errors, info and warnings. */
  fun create(prefix: String): ConsoleProgressIndicator =
    object : ConsoleProgressIndicator(canPrintProgress = isTerminalSmart) {
      override fun logError(s: String, e: Throwable?) {
        super.logError(prefix + s, e)
      }

      override fun logInfo(s: String) {
        super.logInfo(prefix + s)
      }

      override fun logWarning(s: String, e: Throwable?) {
        super.logWarning(prefix + s, e)
      }
    }

  abstract class IsTerminalSmartValueSource : ValueSource<Boolean, ValueSourceParameters.None> {

    override fun obtain(): Boolean = System.getenv("TERM") != "dumb"
  }
}
