/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.artifact.Artifacts
import com.android.build.api.variant.Component
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.Sources
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.AsmClassesTransformRegistration
import com.google.wireless.android.sdk.stats.AsmFramesComputationModeUpdate
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider

abstract class AnalyticsEnabledComponent(
    open val delegate: Component,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
) : Component {

    override val artifacts: Artifacts
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ARTIFACTS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledArtifacts::class.java,
                delegate.artifacts,
                stats,
                objectFactory)
        }

    override val sources: Sources
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSources::class.java,
                delegate.sources,
                stats,
                objectFactory)
        }


    override val javaCompilation: JavaCompilation
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.JAVA_COMPILATION_OPTIONS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledJavaCompilation::class.java,
                delegate.javaCompilation,
                stats,
                objectFactory)
        }

    override val instrumentation: Instrumentation
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledInstrumentation::class.java,
                delegate.instrumentation,
                stats,
                objectFactory
            )
        }

    override val compileClasspath: FileCollection
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPILE_CLASSPATH_VALUE
            return delegate.compileClasspath
        }

    override val compileConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.COMPILE_CONFIGURATION_VALUE
            return delegate.compileConfiguration
        }

    override val runtimeConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RUNTIME_CONFIGURATION_VALUE
            return delegate.runtimeConfiguration
        }

    override val annotationProcessorConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANNOTATION_PROCESSOR_CONFIGURATION_VALUE
            return delegate.annotationProcessorConfiguration
        }

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ASM_TRANSFORM_CLASSES_VALUE
        stats.addAsmClassesTransformsBuilder()
            .setClassVisitorFactoryClassName(classVisitorFactoryImplClass.name)
            .setScope(
                when(scope) {
                    InstrumentationScope.PROJECT -> AsmClassesTransformRegistration.Scope.PROJECT
                    InstrumentationScope.ALL -> AsmClassesTransformRegistration.Scope.ALL
                }
            )
            .build()
        delegate.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ASM_FRAMES_COMPUTATION_NODE_VALUE
        stats.addFramesComputationModeUpdatesBuilder().mode =
            when (mode) {
                FramesComputationMode.COPY_FRAMES -> AsmFramesComputationModeUpdate.Mode.COPY_FRAMES
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS ->
                    AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES ->
                    AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
                FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES ->
                    AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_ALL_CLASSES
            }
        delegate.setAsmFramesComputationMode(mode)
    }

    override val buildType: String?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.BUILD_TYPE_VALUE
            return delegate.buildType
        }

    override val productFlavors: List<Pair<String, String>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PRODUCT_FLAVORS_VALUE
            return delegate.productFlavors
        }

    override val flavorName: String?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.FLAVOR_NAME_VALUE
            return delegate.flavorName
        }

    override val namespace: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NAMESPACE_VALUE
            return delegate.namespace
        }

    override val name: String
        get() = delegate.name
}
