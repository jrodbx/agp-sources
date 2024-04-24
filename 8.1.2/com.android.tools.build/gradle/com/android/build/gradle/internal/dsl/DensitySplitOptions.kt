/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.api.dsl.DensitySplit
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.resources.Density
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import java.util.*
import javax.inject.Inject

abstract class DensitySplitOptions @Inject @WithLazyInitialization(methodName = "lazyInit") constructor(
    val dslServices: DslServices
) : SplitOptions(), DensitySplit {

    protected fun lazyInit() {
        isStrict = true
        init()
    }

    protected abstract var _isEnable: Boolean

    override var isEnable: Boolean
        get() = _isEnable
        set(value) {
            dslServices.deprecationReporter.reportDeprecatedApi(
                newApiElement = null,
                oldApiElement = "splits.density",
                url = "https://developer.android.com/studio/build/configure-apk-splits#configure-density-split",
                deprecationTarget = DeprecationReporter.DeprecationTarget.DENSITY_SPLIT_API
            )
            _isEnable = value
        }

    override fun getDefaultValues(): Set<String> {
        val values = Density.getRecommendedValuesForDevice()
        val fullList: MutableSet<String> = Sets.newHashSetWithExpectedSize(values.size)
        for (value in values) {
            fullList.add(value.resourceValue)
        }
        return fullList
    }

    override fun getAllowedValues(): ImmutableSet<String> {
        val builder = ImmutableSet.builder<String>()
        for (value in Density.values()) {
            if (value != Density.NODPI && value != Density.ANYDPI) {
                builder.add(value.resourceValue)
            }
        }
        return builder.build()
    }

    fun setCompatibleScreens(sizes: List<String>) {
        val copiedValues = sizes.toList()
        compatibleScreens.clear()
        compatibleScreens.addAll(copiedValues)
    }

    override fun compatibleScreens(vararg sizes: String) {
        compatibleScreens.addAll(listOf(*sizes))
    }

    /**
     * Sets whether the build system should determine the splits based on the density folders
     * in the resources.
     *
     *
     * If the auto mode is set to true, the include list will be ignored.
     *
     * @param auto true to automatically set the splits list based on the folders presence, false
     * to use the include list.
     *
     */
    @Deprecated("DensitySplitOptions.auto is not supported anymore.")
    fun setAuto(auto: Boolean) {
        throw RuntimeException("DensitySplitOptions.auto is not supported anymore.")
    }
}
