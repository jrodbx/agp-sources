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

package com.android.build.gradle.internal.multiplatform

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.api.variant.impl.KmpAndroidCompilationType
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTargetImpl
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.dependency.configureKotlinTestDependencyForInstrumentedTestCompilation
import com.android.build.gradle.internal.dependency.configureKotlinTestDependencyForUnitTestCompilation
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker.Companion.android
import com.android.build.gradle.internal.ide.kmp.KotlinIdeImportConfigurator
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.KOTLIN_MPP_PLUGIN_ID
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.ide.common.gradle.Version
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinTargetDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createExternalKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.external.sourcesJarTask

@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinMultiplatformAndroidHandlerImpl(
    private val project: Project,
    private val dslServices: DslServices,
    private val objectFactory: ObjectFactory
): KotlinMultiplatformAndroidHandler {

    private lateinit var kotlinExtension: KotlinMultiplatformExtension
    private lateinit var androidExtension: KotlinMultiplatformAndroidExtensionImpl
    private lateinit var androidTarget: KotlinMultiplatformAndroidTargetImpl

    private lateinit var mainVariant: KmpVariantImpl

    private val sourceSetToCreationConfigMap = mutableMapOf<KotlinSourceSet, KmpComponentCreationConfig>()

    private val extraSourceSetsToIncludeInResolution = mutableSetOf<KotlinSourceSet>()

    override fun createAndroidExtension(): KotlinMultiplatformAndroidExtensionImpl {
        val extensionImplClass = androidPluginDslDecorator
            .decorate(KotlinMultiplatformAndroidExtensionImpl::class.java)

        androidExtension = dslServices.newInstance(
            extensionImplClass,
            dslServices,
            objectFactory,
            { compilationBuilder: KotlinMultiplatformAndroidCompilationBuilder ->
                if (project.pluginManager.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
                    createCompilation(
                        compilationName = compilationBuilder.compilationName,
                        defaultSourceSetName = compilationBuilder.defaultSourceSetName,
                        compilationToAssociateWith = listOf(androidTarget.compilations.getByName(
                            KmpAndroidCompilationType.MAIN.defaultCompilationName
                        ))
                    )
                }
            }
        )

        project.pluginManager.withPlugin(KOTLIN_MPP_PLUGIN_ID) {
            getKotlinPluginVersionFromPlugin(
                project.plugins.findPlugin(KOTLIN_MPP_PLUGIN_ID)!!
            )?.let {
                val kotlinPluginVersion = Version.parse(it)

                if (kotlinPluginVersion < MINIMUM_SUPPORTED_KOTLIN_MULTIPLATFORM_VERSION) {
                    throw RuntimeException("The version of the applied kotlin multiplatform plugin " +
                            "`$it` is less than the minimum supported version by the " +
                            "android plugin. Upgrade your kotlin version to at least " +
                            "`$MINIMUM_SUPPORTED_KOTLIN_MULTIPLATFORM_VERSION` " +
                            "in order to enable the android target.")
                }
            }

            kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension

            androidTarget = kotlinExtension.createExternalKotlinTarget {
                targetName = KotlinMultiplatformAndroidPlugin.ANDROID_TARGET_NAME
                platformType = KotlinPlatformType.jvm
                targetFactory = ExternalKotlinTargetDescriptor.TargetFactory { delegate ->
                    KotlinMultiplatformAndroidTargetImpl(
                        delegate, kotlinExtension, androidExtension
                    )
                }
                configureIdeImport {
                    KotlinIdeImportConfigurator.configure(
                        project,
                        lazy { androidTarget },
                        androidExtension,
                        this,
                        sourceSetToCreationConfigMap = lazy {
                            addSourceSetsThatShouldBeResolvedAsAndroid()
                            sourceSetToCreationConfigMap
                        },
                        extraSourceSetsToIncludeInResolution = lazy {
                            addSourceSetsThatShouldBeResolvedAsAndroid()
                            extraSourceSetsToIncludeInResolution
                        }
                    )
                }
            }

            (kotlinExtension as ExtensionAware).extensions.add(
                KotlinMultiplatformAndroidTarget::class.java,
                KotlinMultiplatformAndroidPlugin.ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME,
                androidTarget
            )

            val mainCompilation = createCompilation(
                compilationName = KmpAndroidCompilationType.MAIN.defaultCompilationName,
                defaultSourceSetName = KmpAndroidCompilationType.MAIN.defaultSourceSetName,
                compilationToAssociateWith = emptyList()
            )

            androidExtension.androidTestOnJvmBuilder?.let { jvmConfiguration ->
                createCompilation(
                    compilationName = jvmConfiguration.compilationName,
                    defaultSourceSetName = jvmConfiguration.defaultSourceSetName,
                    compilationToAssociateWith = listOf(mainCompilation)
                )
            }

            androidExtension.androidTestOnDeviceBuilder?.let { deviceConfiguration ->
                createCompilation(
                    compilationName = deviceConfiguration.compilationName,
                    defaultSourceSetName = deviceConfiguration.defaultSourceSetName,
                    compilationToAssociateWith = listOf(mainCompilation)
                )
            }
        }

        return androidExtension
    }

    override fun getAndroidTarget() = androidTarget

    override fun finalize(variant: KmpVariantImpl) {
        if (variant.sources.java != null) {
            androidTarget.sourcesJarTask(
                variant.androidKotlinCompilation as DecoratedExternalKotlinCompilation
            ).configure {
                it.from(variant.sources.java!!.all) { spec ->
                    spec.into(
                        variant.androidKotlinCompilation.defaultSourceSet.name
                    )
                    spec.duplicatesStrategy = DuplicatesStrategy.WARN
                }
            }
        }

        mainVariant = variant

        listOfNotNull(mainVariant, mainVariant.unitTest, mainVariant.androidDeviceTest).forEach {
            it.androidKotlinCompilation.kotlinSourceSets.forEach { sourceSet ->
                sourceSetToCreationConfigMap[sourceSet] = it
            }
        }

        mainVariant.unitTest?.let {
            configureKotlinTestDependencyForUnitTestCompilation(
                project,
                it,
                kotlinExtension
            )
        }

        mainVariant.androidDeviceTest?.let {
            configureKotlinTestDependencyForInstrumentedTestCompilation(
                project,
                it,
                kotlinExtension
            )
        }
    }

    private fun createCompilation(
        compilationName: String,
        defaultSourceSetName: String,
        compilationToAssociateWith: List<KotlinMultiplatformAndroidCompilation>
    ): KotlinMultiplatformAndroidCompilation {
        kotlinExtension.sourceSets.maybeCreate(
            defaultSourceSetName
        ).apply {
            android = KotlinAndroidSourceSetMarker()
        }
        return androidTarget.compilations.maybeCreate(
            compilationName
        ).also { main ->
            compilationToAssociateWith.forEach { other ->
                main.associateWith(other)
            }
        }
    }

    private fun addSourceSetsThatShouldBeResolvedAsAndroid() {
        // Here we check if there are sourceSets that are included only in the androidTarget, this
        // means that the sourceSet should be treated as android sourceSet in IDE Import and its
        // dependencies should be resolved from the component that maps to the compilation
        // containing this sourceSet.
        kotlinExtension.sourceSets.mapNotNull { sourceSet ->
            val targetsContainingSourceSet = kotlinExtension.targets.filter { target ->
                target.platformType != KotlinPlatformType.common &&
                        target.compilations.any { compilation ->
                            compilation.allKotlinSourceSets.contains(sourceSet)
                        }
            }
            sourceSet.takeIf { targetsContainingSourceSet.singleOrNull() == androidTarget }
        }.forEach { commonSourceSet ->
            for (component in listOfNotNull(mainVariant, mainVariant.unitTest, mainVariant.androidDeviceTest)) {
                if (component.androidKotlinCompilation.allKotlinSourceSets.contains(commonSourceSet)) {
                    sourceSetToCreationConfigMap[commonSourceSet] = component
                    extraSourceSetsToIncludeInResolution.add(commonSourceSet)
                    break
                }
            }
        }
    }

    companion object {
        private val MINIMUM_SUPPORTED_KOTLIN_MULTIPLATFORM_VERSION = Version.parse("1.9.20-Beta")
    }
}
