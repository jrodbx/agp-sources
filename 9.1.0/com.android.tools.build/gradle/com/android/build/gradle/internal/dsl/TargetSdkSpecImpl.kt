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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.TargetSdkSpec
import com.android.build.api.dsl.TargetSdkVersion
import com.android.build.gradle.internal.services.DslServices
import com.android.sdklib.SdkVersionInfo
import javax.inject.Inject

abstract class TargetSdkSpecImpl @Inject constructor(dslService: DslServices) : TargetSdkSpec {

  override fun release(version: Int): TargetSdkVersion {
    return TargetSdkVersionImpl(apiLevel = version, codeName = null)
  }

  override fun preview(codeName: String): TargetSdkVersion {
    val apiLevel = SdkVersionInfo.getApiByBuildCode(codeName, true) - 1
    return TargetSdkVersionImpl(apiLevel, codeName)
  }
}

internal data class TargetSdkVersionImpl(override val apiLevel: Int?, override val codeName: String?) : TargetSdkVersion
