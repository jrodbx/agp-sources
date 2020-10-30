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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.errors.DeprecationReporter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/** Features that apply to distribution by the bundle  */
open class BundleOptions @Inject constructor(
    objectFactory: ObjectFactory,
    deprecationReporter: DeprecationReporter
) {

    val abi: BundleOptionsAbi = objectFactory.newInstance(BundleOptionsAbi::class.java)
    val density: BundleOptionsDensity = objectFactory.newInstance(BundleOptionsDensity::class.java)
    val language: BundleOptionsLanguage = objectFactory.newInstance(BundleOptionsLanguage::class.java)
    val texture: BundleOptionsTexture = objectFactory.newInstance(BundleOptionsTexture::class.java)
    val integrityConfigDir: DirectoryProperty = objectFactory.directoryProperty()

    fun abi(action: Action<BundleOptionsAbi>) {
        action.execute(abi)
    }

    fun density(action: Action<BundleOptionsDensity>) {
        action.execute(density)
    }

    fun language(action: Action<BundleOptionsLanguage>) {
        action.execute(language)
    }

    fun texture(action: Action<BundleOptionsTexture>) {
        action.execute(texture)
    }
}
