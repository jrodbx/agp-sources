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

package com.android.build.api.component.impl.features

import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.Serializable

class BuildConfigCreationConfigImpl(
    private val component: ComponentCreationConfig,
    private val dslInfo: BuildConfigDslInfo,
    private val internalServices: VariantServices
): BuildConfigCreationConfig {

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            BuildConfigField::class.java,
            dslInfo.getBuildConfigFields()
        )
    }

    override val dslBuildConfigFields: Map<String, BuildConfigField<out Serializable>>
        get() = dslInfo.getBuildConfigFields()

    override val compiledBuildConfig: FileCollection
        get() = if (buildConfigType == BuildConfigType.JAR) {
            val files = mutableListOf<Provider<RegularFile>>()
            if (component !is HostTestCreationConfig) {
                files.add(component.artifacts.get(InternalArtifactType.COMPILE_BUILD_CONFIG_JAR))
            }
            if (component is TestComponentCreationConfig) {
                files.add(component.mainVariant.artifacts.get(
                    InternalArtifactType.COMPILE_BUILD_CONFIG_JAR))
            }
            internalServices.fileCollection(files)
        } else {
            internalServices.fileCollection()
        }

    override val buildConfigType: BuildConfigType
        get() = if (internalServices.projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            // TODO(b/224758957): This is wrong we need to check the final build config fields from
            //  the variant API
            && dslBuildConfigFields.none()
        ) {
            BuildConfigType.JAR
        } else {
            BuildConfigType.JAVA_SOURCE
        }
}
