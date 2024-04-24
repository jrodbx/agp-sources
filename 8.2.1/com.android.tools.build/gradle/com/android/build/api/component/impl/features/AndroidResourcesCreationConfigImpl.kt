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

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.base.Preconditions
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

class AndroidResourcesCreationConfigImpl(
    private val component: ComponentCreationConfig,
    private val dslInfo: ComponentDslInfo,
    private val androidResourcesDsl: AndroidResourcesDslInfo,
    private val internalServices: VariantServices,
): AndroidResourcesCreationConfig {

    override val androidResources: AndroidResources by lazy {
        initializeAaptOptionsFromDsl(androidResourcesDsl.androidResources, internalServices)
    }
    override val pseudoLocalesEnabled: Property<Boolean> by lazy {
        internalServices.newPropertyBackingDeprecatedApi(
            Boolean::class.java,
            androidResourcesDsl.isPseudoLocalesEnabled
        )
    }

    override val isCrunchPngs: Boolean
        get() {
            // If set for this build type, respect that.
            val buildTypeOverride = androidResourcesDsl.isCrunchPngs
            if (buildTypeOverride != null) {
                return buildTypeOverride
            }
            // Otherwise, if set globally, respect that.
            val globalOverride = (androidResourcesDsl.androidResources as AaptOptions).cruncherEnabledOverride

            // If not overridden, use the default from the build type.
            return globalOverride ?: androidResourcesDsl.isCrunchPngsDefault
        }

    // Resource shrinker expects MergeResources task to have all the resources merged and with
    // overlay rules applied, so we have to go through the MergeResources pipeline in case it's
    // enabled, see b/134766811.
    override val isPrecompileDependenciesResourcesEnabled: Boolean
        get() = internalServices.projectOptions[BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES] &&
                !useResourceShrinker

    override val resourceConfigurations: Set<String>
        get() = androidResourcesDsl.resourceConfigurations
    override val vectorDrawables: VectorDrawablesOptions
        get() = androidResourcesDsl.vectorDrawables

    override val useResourceShrinker: Boolean
        get() {
            if (component !is ConsumableCreationConfig ||
                !component.optimizationCreationConfig.resourcesShrink ||
                dslInfo.componentType.isForTesting) {
                return false
            }
            if (!component.optimizationCreationConfig.minifiedEnabled) {
                internalServices
                    .issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC,
                        "Removing unused resources requires unused code shrinking to be turned on. See "
                                + "http://d.android.com/r/tools/shrink-resources.html "
                                + "for more information.")
                return false
            }
            return true
        }

    override val compiledRClassArtifact: Provider<RegularFile>
        get() {
            return if (component.global.namespacedAndroidResources) {
                component.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
            } else {
                val componentType = dslInfo.componentType

                if (componentType == ComponentTypeImpl.ANDROID_TEST) {
                    component.artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                } else if (componentType == ComponentTypeImpl.UNIT_TEST) {
                    getRJarForUnitTests()
                } else {
                    // TODO(b/138780301): Also use it in android tests.
                    val useCompileRClassInApp = (internalServices
                        .projectOptions[BooleanOption
                        .ENABLE_APP_COMPILE_TIME_R_CLASS]
                            && !componentType.isForTesting)
                    if (componentType.isAar || useCompileRClassInApp) {
                        component.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
                    } else {
                        Preconditions.checkState(
                            componentType.isApk,
                            "Expected APK type but found: $componentType"
                        )
                        component.artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                    }
                }
            }
        }

    override fun getCompiledRClasses(
        configType: AndroidArtifacts.ConsumedConfigType
    ): FileCollection {
        return if (component.global.namespacedAndroidResources) {
            internalServices.fileCollection().also { fileCollection ->
                val namespacedRClassJar = component.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR)
                val fileTree = internalServices.fileTree(namespacedRClassJar).builtBy(namespacedRClassJar)
                fileCollection.from(fileTree)
                fileCollection.from(
                    component.variantDependencies.getArtifactFileCollection(
                        configType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.SHARED_CLASSES
                    )
                )
                (component as? TestComponentCreationConfig)?.mainVariant?.let {
                    fileCollection.from(it.artifacts.get(InternalArtifactType.COMPILE_R_CLASS_JAR).get())
                }
            }
        } else {
            internalServices.fileCollection(compiledRClassArtifact)
        }
    }

    private fun getRJarForUnitTests(): Provider<RegularFile> {
        Preconditions.checkState(
            component.componentType === ComponentTypeImpl.UNIT_TEST && component is UnitTestCreationConfig,
            "Expected unit test type but found: ${component.componentType}"
        )
        val mainVariant = (component as UnitTestCreationConfig).mainVariant
        return if (mainVariant.componentType.isAar) {
            component.artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
        } else {
            Preconditions.checkState(
                mainVariant.componentType.isApk,
                "Expected APK type but found: " + mainVariant.componentType
            )
            mainVariant
                .artifacts
                .get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
        }
    }
}
