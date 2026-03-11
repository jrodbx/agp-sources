/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
import com.android.build.api.dsl.TargetSdkSpec
import com.android.build.api.dsl.TargetSdkVersion
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.updateIfChanged
import javax.inject.Inject
import org.gradle.api.Action

abstract class KotlinMultiplatformAndroidHostTestImpl @Inject constructor(val dslServices: DslServices) :
  KotlinMultiplatformAndroidHostTest {
  override var isReturnDefaultValues: Boolean = false
  override var isIncludeAndroidResources: Boolean = false
  override var enableCoverage: Boolean = false

  abstract var _targetSdk: TargetSdkVersion?

  override fun targetSdk(action: TargetSdkSpec.() -> Unit) {
    createTargetSdkSpec().also {
      action.invoke(it)
      updateIfChanged(_targetSdk, it.version) { _targetSdk = it }
    }
  }

  open fun targetSdk(action: Action<TargetSdkSpec>) {
    createTargetSdkSpec().also {
      action.execute(it)
      updateIfChanged(_targetSdk, it.version) { _targetSdk = it }
    }
  }

  private fun createTargetSdkSpec(): TargetSdkSpecImpl {
    return dslServices.newDecoratedInstance(TargetSdkSpecImpl::class.java, dslServices).also { it.version = _targetSdk }
  }
}
