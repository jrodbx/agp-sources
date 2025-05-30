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

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.builder.model.ClassField
import java.io.Serializable

class BuildConfigDslInfoImpl(
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
): BuildConfigDslInfo {
    override fun getBuildConfigFields(): Map<String, BuildConfigField<out Serializable>> {
        val buildConfigFieldsMap =
            mutableMapOf<String, BuildConfigField<out Serializable>>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            if (!buildConfigFieldsMap.containsKey(classField.name)) {
                buildConfigFieldsMap[classField.name] =
                    BuildConfigField(classField.type , classField.value, comment)
            }
        }

        (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from build type: ${buildTypeObj.name}")
        }

        for (flavor in productFlavorList) {
            (flavor as com.android.build.gradle.internal.dsl.ProductFlavor).buildConfigFields.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Field from product flavor: ${flavor.name}"
                )
            }
        }
        defaultConfig.buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from default config.")
        }
        return buildConfigFieldsMap
    }
}
