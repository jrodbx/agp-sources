/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.dsl.MinSdkSpec
import com.android.build.api.dsl.MinSdkVersion
import com.android.build.api.dsl.SettingsExtension
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

abstract class FusedLibraryExtensionImpl @Inject constructor(val dslServices: DslServices) :
    InternalFusedLibraryExtension {

    private val minSdkDelegate: MinSdkDelegate = MinSdkDelegate(
        getMinSdk = { _minSdkVersion },
        setMinSdk = { _minSdkVersion = it },
        dslServices = dslServices
    )

    abstract override var namespace: String?

    protected abstract var _minSdkVersion: MinSdkVersion?

    override val minSdkApiLevel: Int?
        get() = minSdkDelegate.minSdkVersion?.apiLevel

    override fun minSdk(action: Action<MinSdkSpec>) {
        minSdkDelegate.minSdk(action)
    }

    override fun minSdk(action: MinSdkSpec.() -> Unit) {
        minSdkDelegate.minSdk(action)
    }

    abstract override val manifestPlaceholders: MutableMap<String, String>

    abstract override val experimentalProperties: MutableMap<String, Any>

    /* Applies options from the settings plugin if they're not set explicitly in
     `[FusedLibraryExtension]`.
     */
    private fun setFieldsFromSettingsExtension(
        settings: SettingsExtension?
    ) {
        settings?.minSdk?.let { minSdk ->
            this.minSdkDelegate.setMinSdkVersion(minSdk)
        }

        settings?.minSdkPreview?.let { minSdkPreview ->
            this.minSdkDelegate.setMinSdkVersion(minSdkPreview)
        }
    }

    companion object {

        fun getDecoratedInstance(
            dslServices: DslServices,
            settingsExtension: SettingsExtension? = null
        ): FusedLibraryExtensionImpl {
            return dslServices.newDecoratedInstance(
                FusedLibraryExtensionImpl::class.java,
                dslServices,
            ).also {
                it.setFieldsFromSettingsExtension(settingsExtension)
            }
        }
    }
}
