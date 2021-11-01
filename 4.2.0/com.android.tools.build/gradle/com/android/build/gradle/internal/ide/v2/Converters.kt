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

package com.android.build.gradle.internal.ide.v2

import com.android.build.api.dsl.AaptOptions
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.LintOptions
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.builder.model.TestOptions
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.ide.AaptOptions.Namespacing.DISABLED
import com.android.builder.model.v2.ide.AaptOptions.Namespacing.REQUIRED
import com.android.builder.model.v2.ide.CodeShrinker
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.TestInfo
import com.android.build.api.dsl.SigningConfig as DslSigningConfig
import com.android.build.gradle.internal.dsl.BuildType as DslBuildType
import com.android.build.gradle.internal.dsl.DefaultConfig as DslDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as DslProductFlavor
import com.android.build.gradle.internal.dsl.VectorDrawablesOptions as DslVectorDrawablesOptions
import com.android.builder.model.ApiVersion as DslApiVersion
import com.android.builder.model.ClassField as DslClassField
import com.android.builder.model.CodeShrinker as CodeShrinkerV1

// Converts DSL items into v2 model instances

internal fun DslDefaultConfig.convert() = ProductFlavorImpl(
    name = name,
    dimension = dimension,
    applicationId = applicationId,
    versionCode = versionCode,
    versionName = versionName,
    minSdkVersion = minSdkVersion?.convert(),
    targetSdkVersion = targetSdkVersion?.convert(),
    maxSdkVersion = maxSdkVersion,
    renderscriptTargetApi = renderscriptTargetApi,
    renderscriptSupportModeEnabled = renderscriptSupportModeEnabled,
    renderscriptSupportModeBlasEnabled = renderscriptSupportModeBlasEnabled,
    renderscriptNdkModeEnabled = renderscriptNdkModeEnabled,
    testApplicationId = testApplicationId,
    testInstrumentationRunner = testInstrumentationRunner,
    testInstrumentationRunnerArguments = testInstrumentationRunnerArguments,
    testHandleProfiling = testHandleProfiling,
    testFunctionalTest = testFunctionalTest,
    resourceConfigurations = resourceConfigurations,
    signingConfig = signingConfig?.name,
    vectorDrawables = vectorDrawables.convert(),
    wearAppUnbundled = wearAppUnbundled,
    applicationIdSuffix = applicationIdSuffix,
    versionNameSuffix = versionNameSuffix,
    buildConfigFields = buildConfigFields.convert(),
    resValues = resValues.convert(),
    proguardFiles = proguardFiles,
    consumerProguardFiles = consumerProguardFiles,
    testProguardFiles = testProguardFiles,
    manifestPlaceholders = manifestPlaceholders,
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard
)

internal fun DslProductFlavor.convert() = ProductFlavorImpl(
    name = name,
    dimension = dimension,
    applicationId = applicationId,
    versionCode = versionCode,
    versionName = versionName,
    minSdkVersion = minSdkVersion?.convert(),
    targetSdkVersion = targetSdkVersion?.convert(),
    maxSdkVersion = maxSdkVersion,
    renderscriptTargetApi = renderscriptTargetApi,
    renderscriptSupportModeEnabled = renderscriptSupportModeEnabled,
    renderscriptSupportModeBlasEnabled = renderscriptSupportModeBlasEnabled,
    renderscriptNdkModeEnabled = renderscriptNdkModeEnabled,
    testApplicationId = testApplicationId,
    testInstrumentationRunner = testInstrumentationRunner,
    testInstrumentationRunnerArguments = testInstrumentationRunnerArguments,
    testHandleProfiling = testHandleProfiling,
    testFunctionalTest = testFunctionalTest,
    resourceConfigurations = resourceConfigurations,
    signingConfig = signingConfig?.name,
    vectorDrawables = vectorDrawables.convert(),
    wearAppUnbundled = wearAppUnbundled,
    applicationIdSuffix = applicationIdSuffix,
    versionNameSuffix = versionNameSuffix,
    buildConfigFields = buildConfigFields.convert(),
    resValues = resValues.convert(),
    proguardFiles = proguardFiles,
    consumerProguardFiles = consumerProguardFiles,
    testProguardFiles = testProguardFiles,
    manifestPlaceholders = manifestPlaceholders,
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard
 )

internal fun DslBuildType.convert() = BuildTypeImpl(
    name = name,
    isDebuggable = isDebuggable,
    isTestCoverageEnabled = isTestCoverageEnabled,
    isPseudoLocalesEnabled = isPseudoLocalesEnabled,
    isJniDebuggable = isJniDebuggable,
    isRenderscriptDebuggable = isRenderscriptDebuggable,
    renderscriptOptimLevel = renderscriptOptimLevel,
    isMinifyEnabled = isMinifyEnabled,
    isZipAlignEnabled = isZipAlignEnabled,
    isEmbedMicroApp = isEmbedMicroApp,
    signingConfig = signingConfig?.name,
    applicationIdSuffix = applicationIdSuffix,
    versionNameSuffix = versionNameSuffix,
    buildConfigFields = buildConfigFields.convert(),
    resValues = resValues.convert(),
    proguardFiles = proguardFiles,
    consumerProguardFiles = consumerProguardFiles,
    testProguardFiles = testProguardFiles,
    manifestPlaceholders = manifestPlaceholders,
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard
)

internal fun DslSigningConfig.convert() = SigningConfigImpl(
    name = name,
    storeFile = storeFile,
    storePassword = storePassword,
    keyAlias = keyAlias,
    keyPassword = keyPassword,
    enableV1Signing = enableV1Signing,
    enableV2Signing = enableV2Signing,
    enableV3Signing = enableV3Signing,
    enableV4Signing = enableV4Signing
)

private fun Map<String, DslClassField>.convert(): Map<String, ClassField> {
    return asSequence().map { it.key to it.value.convert() }.toMap()
}

private fun DslClassField.convert() = ClassFieldImpl(
    type = type,
    name = name,
    value = value,
    documentation = documentation,
    annotations = annotations
)

private fun DslVectorDrawablesOptions.convert() = VectorDrawableOptionsImpl(
    generatedDensities = generatedDensities?.toSet(),
    useSupportLibrary = useSupportLibrary
)

internal fun DslApiVersion.convert() = ApiVersionImpl(
    apiLevel = apiLevel,
    codename = codename
)

internal fun DefaultAndroidSourceSet.convert(features: BuildFeatureValues) = SourceProviderImpl(
    name = name,
    manifestFile = manifestFile,
    javaDirectories = javaDirectories,
    resourcesDirectories = resourcesDirectories,
    aidlDirectories = if (features.aidl) aidlDirectories else null,
    renderscriptDirectories = if (features.renderScript) renderscriptDirectories else null,
    resDirectories = if (features.androidResources) resDirectories else null,
    assetsDirectories = assetsDirectories,
    jniLibsDirectories = jniLibsDirectories,
    shadersDirectories = if (features.shaders) shadersDirectories else null,
    mlModelsDirectories = if (features.mlModelBinding) mlModelsDirectories else null
)

internal fun AaptOptions.convert() = AaptOptionsImpl(
    namespacing = if (namespaced) REQUIRED else DISABLED
)

internal fun LintOptions.convert() = LintOptionsImpl(
    disable = disable,
    enable = enable,
    check = checkOnly,
    lintConfig = lintConfig,
    textReport = textReport,
    textOutput = textOutput,
    htmlOutput = htmlOutput,
    htmlReport = htmlReport,
    xmlReport = xmlReport,
    xmlOutput = xmlOutput,
    sarifReport = sarifReport,
    sarifOutput = sarifOutput,
    isAbortOnError = isAbortOnError,
    isAbsolutePaths = isAbsolutePaths,
    isNoLines = isNoLines,
    isQuiet = isQuiet,
    isCheckAllWarnings = isCheckAllWarnings,
    isIgnoreWarnings = isIgnoreWarnings,
    isWarningsAsErrors = isWarningsAsErrors,
    isShowAll = isShowAll,
    isExplainIssues = isExplainIssues,
    isCheckReleaseBuilds = isCheckReleaseBuilds,
    isCheckTestSources = isCheckTestSources,
    isIgnoreTestSources = isIgnoreTestSources,
    isCheckGeneratedSources = isCheckGeneratedSources,
    isCheckDependencies = isCheckDependencies,
    baselineFile = baselineFile,
    severityOverrides = null
)

internal fun CompileOptions.convert(): JavaCompileOptions {
    return JavaCompileOptionsImpl(
        encoding = encoding,
        sourceCompatibility = sourceCompatibility.toString(),
        targetCompatibility = targetCompatibility.toString(),
        isCoreLibraryDesugaringEnabled = isCoreLibraryDesugaringEnabled
    )
}

internal fun CodeShrinkerV1.convert(): CodeShrinker = when (this) {
    CodeShrinkerV1.PROGUARD -> CodeShrinker.PROGUARD
    CodeShrinkerV1.R8 -> CodeShrinker.R8
}

internal fun TestOptions.Execution.convert(): TestInfo.Execution = when (this) {
    TestOptions.Execution.HOST -> TestInfo.Execution.HOST
    TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR
    TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR
}
