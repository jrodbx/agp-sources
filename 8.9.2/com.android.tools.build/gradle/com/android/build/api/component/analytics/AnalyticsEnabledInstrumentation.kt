/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Instrumentation
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.AsmClassesTransformRegistration
import com.google.wireless.android.sdk.stats.AsmFramesComputationModeUpdate
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

open class AnalyticsEnabledInstrumentation @Inject constructor(
    open val delegate: Instrumentation,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
): Instrumentation {

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.INSTRUMENTATION_TRANSFORM_CLASSES_WITH_VALUE
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
            VariantPropertiesMethodType.INSTRUMENTATION_SET_ASM_FRAMES_COMPUTATUION_MODE_VALUE
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

    override val excludes: SetProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_EXCLUDES_VALUE
            return delegate.excludes
        }
}
