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
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import java.io.Serializable

class BuildConfigCreationConfigImpl(
    private val component: ConsumableCreationConfig,
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
        get() {
            val isBuildConfigJar = buildConfigType == BuildConfigType.JAR
            // BuildConfig JAR is not required to be added as a classpath for ANDROID_TEST and UNIT_TEST
            // variants as the tests will use JAR from GradleTestProject which doesn't use testedConfig.
            return if (isBuildConfigJar && component !is TestComponentCreationConfig) {
                internalServices.fileCollection(
                    component.artifacts.get(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR
                    )
                )
            } else {
                internalServices.fileCollection()
            }
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
