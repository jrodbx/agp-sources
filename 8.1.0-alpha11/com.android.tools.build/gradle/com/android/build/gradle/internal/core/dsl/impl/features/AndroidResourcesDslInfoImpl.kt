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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.builder.model.ClassField
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableSet

internal class AndroidResourcesDslInfoImpl(
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
    private val mergedFlavor: MergedFlavor,
    private val extension: CommonExtension<*, *, *, *, *>
): AndroidResourcesDslInfo {
    override val androidResources: AndroidResources
        get() = extension.androidResources

    // build type delegates

    override val isPseudoLocalesEnabled: Boolean
        get() = buildTypeObj.isPseudoLocalesEnabled

    override val isCrunchPngs: Boolean?
        get() {
            return when (buildTypeObj) {
                is ApplicationBuildType -> buildTypeObj.isCrunchPngs
                is DynamicFeatureBuildType -> buildTypeObj.isCrunchPngs
                else -> false
            }
        }

    override val resourceConfigurations: ImmutableSet<String>
        get() = ImmutableSet.copyOf(mergedFlavor.resourceConfigurations)

    override val vectorDrawables: VectorDrawablesOptions
        get() = mergedFlavor.vectorDrawables

    override val isCrunchPngsDefault: Boolean
        // does not exist in the new DSL
        get() = (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).isCrunchPngsDefault

    override fun getResValues(): Map<ResValue.Key, ResValue> {
        val resValueFields = mutableMapOf<ResValue.Key, ResValue>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            val key = ResValueKeyImpl(classField.type, classField.name)
            if (!resValueFields.containsKey(key)) {
                resValueFields[key] = ResValue(
                    value = classField.value,
                    comment = comment
                )
            }
        }

        (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from build type: ${buildTypeObj.name}")
        }

        productFlavorList.forEach { flavor ->
            (flavor as com.android.build.gradle.internal.dsl.ProductFlavor)
                .resValues.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Value from product flavor: ${flavor.name}"
                )
            }
        }

        defaultConfig.resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from default config.")
        }

        return resValueFields
    }
}
