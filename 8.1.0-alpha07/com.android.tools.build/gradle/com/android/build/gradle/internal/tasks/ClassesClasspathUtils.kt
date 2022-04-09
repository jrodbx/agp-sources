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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.FileCollection

class ClassesClasspathUtils(
    val creationConfig: ApkCreationConfig,
    enableDexingArtifactTransform: Boolean,
    classesAlteredTroughVariantAPI: Boolean,
) {

    val projectClasses: FileCollection

    val subProjectsClasses: FileCollection
    val externalLibraryClasses: FileCollection
    val mixedScopeClasses: FileCollection
    val desugaringClasspathClasses: FileCollection

    // Difference between this property and desugaringClasspathClasses is that for the test
    // variant, this property does not contain tested project code, allowing us to have more
    // cache hits when using artifact transforms.
    val desugaringClasspathForArtifactTransforms: FileCollection
    val dexExternalLibsInArtifactTransform: Boolean

    init {
        // The source of project classes depend on whether; Jacoco instrumentation is enabled,
        // instrumentation is collected using an artifact transform and or
        // the user registers a transform using the legacy Transform
        // [com.android.build.api.transform.Transform].
        //
        // Cases:
        // (1) Jacoco Transform is enabled and there are no registered transforms:
        //     then: Provide the project classes from the artifacts produced by the JacocoTask
        // (2) Jacoco Transform is enabled and a legacy transform is registered:
        //     then: No project classes will be provided. Rather, artifacts produced by the
        //           JacocoTask will be set as the mixed scope classes.
        // (3) No Jacoco Transforms:
        //      then: Provide the project classes from the legacy transform API classes.

        projectClasses = creationConfig.artifacts.forScope(
            if (classesAlteredTroughVariantAPI) ScopedArtifacts.Scope.ALL
            else ScopedArtifacts.Scope.PROJECT
        ).getFinalArtifacts(ScopedArtifact.CLASSES)

        @Suppress("DEPRECATION") // Legacy support
        val desugaringClasspathScopes: MutableSet<QualifiedContent.ScopeType> =
            mutableSetOf(QualifiedContent.Scope.PROVIDED_ONLY)
        if (classesAlteredTroughVariantAPI) {
            subProjectsClasses = creationConfig.services.fileCollection()
            externalLibraryClasses = creationConfig.services.fileCollection()
            mixedScopeClasses = creationConfig.services.fileCollection()
            dexExternalLibsInArtifactTransform = false
        } else if (enableDexingArtifactTransform) {
            subProjectsClasses = creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
            externalLibraryClasses = creationConfig.services.fileCollection()
            mixedScopeClasses = creationConfig.services.fileCollection()
            dexExternalLibsInArtifactTransform = false

            @Suppress("DEPRECATION") // Legacy support
            run {
                desugaringClasspathScopes.add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(QualifiedContent.Scope.TESTED_CODE)
                desugaringClasspathScopes.add(QualifiedContent.Scope.SUB_PROJECTS)
            }
        } else {
            // legacy Transform API
            @Suppress("DEPRECATION") // Legacy support
            subProjectsClasses = creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
            externalLibraryClasses = creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
                .getFinalArtifacts(ScopedArtifact.CLASSES)

            // mixed scoped classes are not possible any longer since each jar is individually
            // present in a single scope.
            mixedScopeClasses = creationConfig.services.fileCollection()

            dexExternalLibsInArtifactTransform =
                creationConfig.services.projectOptions[BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS]
        }

        desugaringClasspathForArtifactTransforms = if (dexExternalLibsInArtifactTransform) {
            val testedExternalLibs = (creationConfig as? TestComponentCreationConfig)?.onTestedVariant {
                it.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR
                ).artifactFiles
            } ?: creationConfig.services.fileCollection()

            // Before b/115334911 was fixed, provided classpath did not contain the tested project.
            // Because we do not want tested variant classes in the desugaring classpath for
            // external libraries, we explicitly remove it.
            val testedProject = (creationConfig as? TestComponentCreationConfig)?.onTestedVariant {
                val artifactType =
                    PublishingSpecs.getVariantPublishingSpec(it.componentType).getSpec(
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
                    )!!.outputType
                creationConfig.services.fileCollection(
                    it.artifacts.get(artifactType)
                )
            } ?: creationConfig.services.fileCollection()

            creationConfig.services.fileCollection(
                getArtifactFiles(desugaringClasspathScopes, ScopedArtifact.CLASSES),
                testedExternalLibs,
                externalLibraryClasses
            ).minus(testedProject)
        } else {
            creationConfig.services.fileCollection()
        }

        @Suppress("DEPRECATION") // Legacy support
        desugaringClasspathClasses =
            getArtifactFiles(desugaringClasspathScopes, ScopedArtifact.CLASSES)
    }

    private fun getArtifactFiles(inputScopes: Collection<QualifiedContent.ScopeType>, type: ScopedArtifact) =
        creationConfig.services.fileCollection().also {
            if (inputScopes.contains(InternalScope.LOCAL_DEPS)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.LOCAL_DEPS)
                        .getFinalArtifacts(type)
                )
            }
            if (inputScopes.contains(InternalScope.FEATURES)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.FEATURES)
                        .getFinalArtifacts(type)
                )
            }
            if (inputScopes.contains(QualifiedContent.Scope.SUB_PROJECTS)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                        .getFinalArtifacts(type)
                )
            }
            if (inputScopes.contains(QualifiedContent.Scope.TESTED_CODE)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.TESTED_CODE)
                        .getFinalArtifacts(type)
                )
            }
            if (inputScopes.contains(QualifiedContent.Scope.PROVIDED_ONLY)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.COMPILE_ONLY)
                        .getFinalArtifacts(type)
                )
            }
            if (inputScopes.contains(QualifiedContent.Scope.EXTERNAL_LIBRARIES)) {
                it.from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
                        .getFinalArtifacts(type)
                )
            }
        }

}
