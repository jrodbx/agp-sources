/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

/**
 * Shared properties between [TestProductFlavor] and [TestDefaultConfig]
 *
 * See [ProductFlavor] and [DefaultConfig] for more information.
 */
interface TestBaseFlavor : BaseFlavor, TestVariantDimension {
  /**
   * The target SDK version. Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters. Only one of
   * [targetSdk] and [targetSdkPreview] should be set.
   *
   * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
   */
  var targetSdk: Int?

  @Deprecated("Will be removed in AGP 10.0", replaceWith = ReplaceWith("targetSdk { version = release(targetSdkVersion) }"))
  fun targetSdkVersion(targetSdkVersion: Int)

  /**
   * The target SDK version. Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters. Only one of
   * [targetSdk] and [targetSdkPreview] should be set.
   *
   * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
   */
  var targetSdkPreview: String?

  /** Configures all aspects regarding target sdk, see [TargetSdkSpec] for available options. */
  fun targetSdk(action: TargetSdkSpec.() -> Unit)

  @Deprecated(message = "Will be removed in AGP 10.0, replaced with the targetSdk block") fun setTargetSdkVersion(targetSdkVersion: String?)

  @Deprecated(message = "Will be removed in AGP 10.0, replaced with the targetSdk block") fun targetSdkVersion(targetSdkVersion: String?)

  /**
   * The maxSdkVersion, or null if not specified. This is only the value set on this produce flavor.
   *
   * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
   */
  var maxSdk: Int?

  /** Configures all aspects regarding maxSdk, see [MaxSdkSpec] for available options. */
  fun maxSdk(action: MaxSdkSpec.() -> Unit)

  @Deprecated("Will be removed in AGP 10.0", replaceWith = ReplaceWith("maxSdk { version = release(maxSdkVersion) }"))
  fun maxSdkVersion(maxSdkVersion: Int)
}
