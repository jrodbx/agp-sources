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

package com.android.build.api.variant

/**
 * Provides an ordered collection of APK batches, each intended for local installation, facilitating staged installations.
 *
 * An instance of [ApkOutput] can be obtained via [ApplicationVariant.outputProviders]
 *
 * See example at [ApkOutputProviders]
 */
interface ApkOutput {
  /** Returns an ordered collection of co-installable APK batches targeted for a specific device. */
  val apkInstallGroups: List<ApkInstallGroup>
}
