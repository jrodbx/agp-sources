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

package com.android.build.gradle.internal.ide.proto

import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.builder.model.proto.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.proto.ide.AndroidVersion
import com.android.builder.model.proto.ide.SigningConfig
import com.android.builder.model.proto.ide.TestInfo
import com.android.builder.model.v2.ide.AndroidLibraryData
import com.android.builder.model.v2.ide.ComponentInfo
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectInfo
import java.io.File

import com.android.builder.model.proto.ide.AndroidLibraryData as AndroidLibraryDataProto
import com.android.builder.model.proto.ide.ComponentInfo as ComponentInfoProto
import com.android.builder.model.proto.ide.Library as LibraryProto
import com.android.builder.model.proto.ide.LibraryInfo as LibraryInfoProto
import com.android.builder.model.proto.ide.LibraryType as LibraryTypeProto
import com.android.builder.model.proto.ide.ProjectInfo as ProjectInfoProto

internal fun<T, R> R.setIfNotNull(
    value: T?,
    setter: R.(T) -> Unit
): R {
    if (value != null) {
        setter(value)
    }
    return this
}

internal fun File.convert() = com.android.builder.model.proto.ide.File.newBuilder()
    .setAbsolutePath(absolutePath)
    .build()

internal fun com.android.builder.model.v2.ide.TestInfo.Execution.convert(): TestInfo.Execution =
    when (this) {
        com.android.builder.model.v2.ide.TestInfo.Execution.HOST -> TestInfo.Execution.HOST
        com.android.builder.model.v2.ide.TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR-> TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR
        com.android.builder.model.v2.ide.TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR
    }

internal fun com.android.builder.model.v2.ide.TestInfo.convert() =
    TestInfo.newBuilder()
        .setAnimationsDisabled(animationsDisabled)
        .setIfNotNull(
            execution?.convert(),
            TestInfo.Builder::setExecution
        )
        .addAllAdditionalRuntimeApks(
            additionalRuntimeApks.map { it.convert() }
        )
        .setInstrumentedTestTaskName(
            instrumentedTestTaskName
        )
        .build()

internal fun com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.convert() =
    when (this) {
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS ->
            AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.ENABLE_VCS_INFO ->
            AndroidGradlePluginProjectFlags.BooleanFlag.ENABLE_VCS_INFO
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.BUILD_FEATURE_ANDROID_RESOURCES ->
            AndroidGradlePluginProjectFlags.BooleanFlag.BUILD_FEATURE_ANDROID_RESOURCES
    }

internal fun com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.convert() =
    AndroidGradlePluginProjectFlags.newBuilder()
        .addAllBooleanFlagValues(
            com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.values()
                .map { flag ->
                    AndroidGradlePluginProjectFlags.BooleanFlagValue.newBuilder()
                        .setFlag(flag.convert())
                        .setValue(flag.getValue(this))
                        .build()
                }
        )
        .build()

internal fun com.android.build.api.variant.AndroidVersion.convert() =
    AndroidVersion.newBuilder()
        .setApiLevel(apiLevel)
        .setIfNotNull(codename, AndroidVersion.Builder::setCodename)

internal fun SigningConfigImpl.convert() =
    SigningConfig.newBuilder()
        .setIfNotNull(name, SigningConfig.Builder::setName)
        .setIfNotNull(storeFile.orNull?.convert(), SigningConfig.Builder::setStoreFile)
        .setIfNotNull(storePassword.orNull, SigningConfig.Builder::setStorePassword)
        .setIfNotNull(keyAlias.orNull, SigningConfig.Builder::setKeyAlias)
        .setIfNotNull(keyPassword.orNull, SigningConfig.Builder::setKeyPassword)
        .setEnableV1Signing(enableV1Signing.get())
        .setEnableV2Signing(enableV2Signing.get())
        .setEnableV3Signing(enableV3Signing.get())
        .setEnableV4Signing(enableV4Signing.get())
        .setIsSigningReady(isSigningReady())

private fun LibraryType.convert() =
    when (this) {
        LibraryType.PROJECT -> LibraryTypeProto.PROJECT
        LibraryType.ANDROID_LIBRARY -> LibraryTypeProto.ANDROID_LIBRARY
        LibraryType.JAVA_LIBRARY -> LibraryTypeProto.JAVA_LIBRARY
        LibraryType.RELOCATED -> LibraryTypeProto.RELOCATED
        LibraryType.NO_ARTIFACT_FILE -> LibraryTypeProto.NO_ARTIFACT_FILE
    }

private fun ComponentInfo.convertComponentInfo() =
    ComponentInfoProto.newBuilder()
        .setIfNotNull(
            buildType, ComponentInfoProto.Builder::setBuildType
        )
        .putAllProductFlavors(productFlavors)
        .putAllAttributes(attributes)
        .addAllCapabilities(capabilities)
        .setIsTestFixtures(isTestFixtures)

private fun ProjectInfo.convert() =
    ProjectInfoProto.newBuilder()
        .setBuildId(buildId)
        .setProjectPath(projectPath)
        .setComponentInfo(
            convertComponentInfo()
        )

private fun LibraryInfo.convert() =
    LibraryInfoProto.newBuilder()
        .setComponentInfo(
            convertComponentInfo()
        )
        .setGroup(group)
        .setName(name)
        .setVersion(version)

private fun AndroidLibraryData.convert() =
    AndroidLibraryDataProto.newBuilder()
        .setManifest(manifest.convert())
        .addAllCompileJarFiles(compileJarFiles.map { it.convert() })
        .addAllRuntimeJarFiles(runtimeJarFiles.map { it.convert() })
        .setResFolder(resFolder.convert())
        .setResStaticLibrary(resStaticLibrary.convert())
        .setAssetsFolder(assetsFolder.convert())
        .setJniFolder(jniFolder.convert())
        .setAidlFolder(aidlFolder.convert())
        .setRenderscriptFolder(renderscriptFolder.convert())
        .setProguardRules(proguardRules.convert())
        .setExternalAnnotations(externalAnnotations.convert())
        .setPublicResources(publicResources.convert())
        .setSymbolFile(symbolFile.convert())

internal fun Library.convert() =
    LibraryProto.newBuilder()
        .setKey(key)
        .setType(type.convert())
        .setIfNotNull(
            projectInfo?.convert(), LibraryProto.Builder::setProjectInfo
        )
        .setIfNotNull(
            libraryInfo?.convert(), LibraryProto.Builder::setLibraryInfo
        )
        .setIfNotNull(
            artifact?.convert(), LibraryProto.Builder::setArtifact
        )
        .setIfNotNull(
            lintJar?.convert(), LibraryProto.Builder::setLintJar
        )
        .setIfNotNull(
            srcJar?.convert(), LibraryProto.Builder::setSrcJar
        )
        .setIfNotNull(
            docJar?.convert(), LibraryProto.Builder::setDocJar
        )
        .setIfNotNull(
            samplesJar?.convert(), LibraryProto.Builder::setSamplesJar
        )
        .setIfNotNull(
            androidLibraryData?.convert(), LibraryProto.Builder::setAndroidLibraryData
        )
