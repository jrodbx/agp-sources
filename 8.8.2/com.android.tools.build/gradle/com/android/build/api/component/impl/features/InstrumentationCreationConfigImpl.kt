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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig
import com.android.build.gradle.internal.dependency.AsmClassesTransform
import com.android.build.gradle.internal.dependency.RecalculateStackFramesTransform
import com.android.build.gradle.internal.dsl.InstrumentationImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory

class InstrumentationCreationConfigImpl(
    private val component: ComponentCreationConfig,
    private val internalServices: VariantServices
): InstrumentationCreationConfig {

    override val instrumentation: InstrumentationImpl by lazy {
        InstrumentationImpl(
            component.services,
            internalServices,
            isLibraryVariant = component is LibraryCreationConfig
        )
    }

    override val asmFramesComputationMode: FramesComputationMode
        get() = instrumentation.finalAsmFramesComputationMode

    override val projectClassesAreInstrumented: Boolean
        get() = registeredProjectClassesVisitors.isNotEmpty() ||
                (component as? ApkCreationConfig)?.advancedProfilingTransforms?.isNotEmpty() == true
    override val dependenciesClassesAreInstrumented: Boolean
        get() = registeredDependenciesClassesVisitors.isNotEmpty() ||
                (component as? ApkCreationConfig)?.advancedProfilingTransforms?.isNotEmpty() == true

    override val projectClassesPostInstrumentation: FileCollection by lazy {
        if (projectClassesAreInstrumented) {
            if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                internalServices.fileCollection(
                    component.artifacts.get(
                        InternalArtifactType.FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_CLASSES
                    ),
                    internalServices.fileCollection(
                        component.artifacts.get(
                            InternalArtifactType.FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_JARS
                        )
                    ).asFileTree
                )
            } else {
                internalServices.fileCollection(
                    component.artifacts.get(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_CLASSES),
                    internalServices.fileCollection(
                        component.artifacts.get(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_JARS)
                    ).asFileTree
                )
            }
        } else {
            component
                .artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
        }
    }
    override fun getDependenciesClassesJarsPostInstrumentation(
        scope: AndroidArtifacts.ArtifactScope
    ): FileCollection {
        return if (dependenciesClassesAreInstrumented) {
            if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                component.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    scope,
                    AndroidArtifacts.ArtifactType.CLASSES_FIXED_FRAMES_JAR,
                    RecalculateStackFramesTransform.getAttributesForConfig(component)
                )
            } else {
                component.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    scope,
                    AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS,
                    AsmClassesTransform.getAttributesForConfig(component)
                )
            }
        } else {
            component.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                scope,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        }
    }

    override val registeredProjectClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredProjectClassesVisitors
    override val registeredDependenciesClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredDependenciesClassesVisitors

    override fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory) {
        instrumentation.configureAndLockAsmClassesVisitors(
            objectFactory,
            component.global.asmApiVersion
        )
    }
}
