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

@file:JvmName("ComponentUtils")

package com.android.build.api.component.impl

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.sdklib.AndroidTargetHash
import com.google.common.base.Strings
import org.gradle.api.file.FileCollection

val ENABLE_LEGACY_API: String =
    "Turn on with by putting '${BooleanOption.ENABLE_LEGACY_API.propertyName}=true in gradle.properties'\n" +
            "Using this deprecated API may still fail, depending on usage of the new Variant API, like computing applicationId via a task output."

/**
 * Determine if the final output should be marked as testOnly to prevent uploading to Play
 * store.
 *
 * <p>Uploading to Play store is disallowed if:
 *
 * <ul>
 *   <li>An injected option is set (usually by the IDE for testing purposes).
 *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
 * </ul>
 *
 * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
 *
 * @param variant {@link VariantCreationConfig} for this variant scope.
 */
internal fun ApkCreationConfig.isTestApk(): Boolean {
    val projectOptions = services.projectOptions

    return projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY) ?: (
            !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
            || global.targetDeployApiFromIDE != null
            || AndroidTargetHash.getVersionFromHash(global.compileSdkHashString)?.isPreview == true
            || minSdk.codename != null
            || targetSdk.codename != null)
}

internal fun<T> ComponentCreationConfig.warnAboutAccessingVariantApiValueForDisabledFeature(
    featureName: String,
    apiName: String,
    value: T
): T {
    services.issueReporter.reportWarning(
        IssueReporter.Type.ACCESSING_DISABLED_FEATURE_VARIANT_API,
        "Accessing value $apiName in variant $name has no effect as the feature" +
                " $featureName is disabled."
    )
    return value
}

internal fun NestedComponentCreationConfig.getMainTargetSdkVersion(): AndroidVersion =
    when (mainVariant) {
        is ApkCreationConfig -> (mainVariant as ApkCreationConfig).targetSdk
        is LibraryCreationConfig -> (mainVariant as LibraryCreationConfig).targetSdk
        else -> minSdk
    }

internal fun getJavaClasspath(
    component: ComponentCreationConfig,
    configType: AndroidArtifacts.ConsumedConfigType,
    classesType: AndroidArtifacts.ArtifactType,
    generatedBytecodeKey: Any?
): FileCollection {
    var mainCollection = component.variantDependencies
        .getArtifactFileCollection(configType, AndroidArtifacts.ArtifactScope.ALL, classesType)
    component.oldVariantApiLegacySupport?.let {
        mainCollection = mainCollection.plus(
            it.variantData.getGeneratedBytecode(generatedBytecodeKey)
        )
    }
    // Add R class jars to the front of the classpath as libraries might also export
    // compile-only classes. This behavior is verified in CompileRClassFlowTest
    // While relying on this order seems brittle, it avoids doubling the number of
    // files on the compilation classpath by exporting the R class separately or
    // and is much simpler than having two different outputs from each library, with
    // and without the R class, as AGP publishing code assumes there is exactly one
    // artifact for each publication.
    mainCollection =
        component.services.fileCollection(
            *listOfNotNull(
                component.androidResourcesCreationConfig?.getCompiledRClasses(configType),
                component.buildConfigCreationConfig?.compiledBuildConfig,
                getCompiledManifest(component),
                mainCollection
            ).toTypedArray()
        )
    return mainCollection
}

private fun getCompiledManifest(component: ComponentCreationConfig): FileCollection {
    val manifestClassRequired = component.componentType.requiresManifest &&
            component.services.projectOptions[BooleanOption.GENERATE_MANIFEST_CLASS]
    val isTest = component.componentType.isForTesting
    val isAar = component.componentType.isAar
    return if (manifestClassRequired && !isAar && !isTest) {
        component.services.fileCollection(
            component.artifacts.get(InternalArtifactType.COMPILE_MANIFEST_JAR)
        )
    } else {
        component.services.fileCollection()
    }
}
