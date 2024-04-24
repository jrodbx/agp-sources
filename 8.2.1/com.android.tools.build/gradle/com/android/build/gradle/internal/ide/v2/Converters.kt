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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.impl.SourceDirectoriesImpl
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.builder.core.DefaultApiVersion
import com.android.builder.model.TestOptions
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.ide.AaptOptions.Namespacing.DISABLED
import com.android.builder.model.v2.ide.AaptOptions.Namespacing.REQUIRED
import com.android.builder.model.v2.ide.CodeShrinker
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.TestInfo
import com.android.build.api.dsl.ApkSigningConfig as DslSigningConfig
import com.android.build.gradle.internal.dsl.BuildType as DslBuildType
import com.android.build.gradle.internal.dsl.DefaultConfig as DslDefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor as DslProductFlavor
import com.android.build.gradle.internal.dsl.VectorDrawablesOptions as DslVectorDrawablesOptions
import com.android.builder.model.ApiVersion as DslApiVersion
import com.android.builder.model.ClassField as DslClassField
import com.android.builder.model.CodeShrinker as CodeShrinkerV1

// Converts DSL items into v2 model instances

internal fun DslDefaultConfig.convert(features: BuildFeatureValues) = ProductFlavorImpl(
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
    resourceConfigurations = resourceConfigurations.toImmutableList(),
    signingConfig = signingConfig?.name,
    vectorDrawables = vectorDrawables.convert(),
    wearAppUnbundled = wearAppUnbundled,
    applicationIdSuffix = applicationIdSuffix,
    versionNameSuffix = versionNameSuffix,
    buildConfigFields = buildConfigFields.convertBuildConfig(features),
    resValues = resValues.convertResValues(features),
    proguardFiles = proguardFiles.toImmutableList(),
    consumerProguardFiles = consumerProguardFiles.toImmutableList(),
    testProguardFiles = testProguardFiles.toImmutableList(),
    manifestPlaceholders = manifestPlaceholders.toImmutableMap(),
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard,
)

internal fun DslProductFlavor.convert(features: BuildFeatureValues) = ProductFlavorImpl(
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
    testInstrumentationRunnerArguments = testInstrumentationRunnerArguments.toImmutableMap(),
    testHandleProfiling = testHandleProfiling,
    testFunctionalTest = testFunctionalTest,
    resourceConfigurations = resourceConfigurations.toImmutableList(),
    signingConfig = signingConfig?.name,
    vectorDrawables = vectorDrawables.convert(),
    wearAppUnbundled = wearAppUnbundled,
    applicationIdSuffix = applicationIdSuffix,
    versionNameSuffix = versionNameSuffix,
    buildConfigFields = buildConfigFields.convertBuildConfig(features),
    resValues = resValues.convertResValues(features),
    proguardFiles = proguardFiles,
    consumerProguardFiles = consumerProguardFiles,
    testProguardFiles = testProguardFiles,
    manifestPlaceholders = manifestPlaceholders.toImmutableMap(),
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard,
    isDefault = isDefault
)

internal fun DslBuildType.convert(features: BuildFeatureValues) = BuildTypeImpl(
    name = name,
    isDebuggable = isDebuggable,
    isProfileable = isProfileable,
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
    buildConfigFields = buildConfigFields.convertBuildConfig(features),
    resValues = resValues.convertResValues(features),
    proguardFiles = proguardFiles.toImmutableList(),
    consumerProguardFiles = consumerProguardFiles.toImmutableList(),
    testProguardFiles = testProguardFiles.toImmutableList(),
    manifestPlaceholders = manifestPlaceholders.toImmutableMap(),
    multiDexEnabled = multiDexEnabled,
    multiDexKeepFile = multiDexKeepFile,
    multiDexKeepProguard = multiDexKeepProguard,
    isDefault = isDefault
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

private fun Map<String, DslClassField>.convertBuildConfig(
    features: BuildFeatureValues
): Map<String, ClassField>? =
    if (features.buildConfig)
        asSequence().map { it.key to it.value.convert() }.toMap()
    else
        null

private fun Map<String, DslClassField>.convertResValues(
    features: BuildFeatureValues
): Map<String, ClassField>? =
    if (features.resValues)
        asSequence().map { it.key to it.value.convert() }.toMap()
    else
        null

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

internal fun DefaultAndroidSourceSet.convert(
        features: BuildFeatureValues,
        variantSources: VariantCreationConfig? = null
): SourceProvider {
    val mixin = variantSources?.sources
    return SourceProviderImpl(
        name = name,
        manifestFile = manifestFile,
        javaDirectories = javaDirectories + variantSourcesForModel(mixin?.java),
        kotlinDirectories = kotlinDirectories + variantSourcesForModel(mixin?.kotlin),
        resourcesDirectories = resourcesDirectories,
        aidlDirectories = if (features.aidl) aidlDirectories else null,
        renderscriptDirectories = if (features.renderScript) renderscriptDirectories else null,
        baselineProfileDirectories = (baselineProfiles as DefaultAndroidSourceDirectorySet).srcDirs
            + variantSourcesForModel(mixin?.baselineProfiles),
        resDirectories = if (features.androidResources) resDirectories else null,
        assetsDirectories = assetsDirectories + variantSourcesForModel(mixin?.assets),
        jniLibsDirectories = jniLibsDirectories + variantSourcesForModel(mixin?.jniLibs),
        shadersDirectories = if (features.shaders) {
            shadersDirectories + variantSourcesForModel(mixin?.shaders)
        } else null,
        mlModelsDirectories = if (features.mlModelBinding) mlModelsDirectories else null,
        customDirectories = customDirectories,
    )
}

internal fun DefaultAndroidSourceSet.convert(
    sources: InternalSources,
) = SourceProviderImpl(
    name = name,
    manifestFile = manifestFile,
    javaDirectories = variantSourcesForModel(sources.java),
    kotlinDirectories = variantSourcesForModel(sources.kotlin),
    resourcesDirectories = variantSourcesForModel(sources.resources),
    aidlDirectories = variantSourcesForModel(sources.aidl),
    renderscriptDirectories = variantSourcesForModel(sources.renderscript),
    baselineProfileDirectories = variantSourcesForModel(sources.baselineProfiles),
    resDirectories = variantSourcesForModel(sources.res),
    assetsDirectories = variantSourcesForModel(sources.assets),
    jniLibsDirectories = variantSourcesForModel(sources.jniLibs),
    shadersDirectories = variantSourcesForModel(sources.shaders),
    mlModelsDirectories = variantSourcesForModel(sources.mlModels),
    customDirectories = customDirectories,
)

/**
 * Returns source dirs that should be added to the model, but avoids getting the generated ones
 * as they are handled separately in the model.
 */
private fun variantSourcesForModel(sourceDirectories: SourceDirectoriesImpl?) =
    sourceDirectories?.variantSourcesForModel { it.shouldBeAddedToIdeModel && !it.isGenerated } ?: emptyList()

internal fun AndroidResources.convert() = AaptOptionsImpl(
    namespacing = if (namespaced) REQUIRED else DISABLED
)

internal fun Lint.convert() = LintOptionsImpl(
    disable = disable.toSet(),
    enable = enable.toSet(),
    informational = informational.toSet(),
    warning = warning.toSet(),
    error = error.toSet(),
    fatal = fatal.toSet(),
    checkOnly = checkOnly.toSet(),
    lintConfig = lintConfig,
    textReport = textReport,
    textOutput = textOutput,
    htmlOutput = htmlOutput,
    htmlReport = htmlReport,
    xmlReport = xmlReport,
    xmlOutput = xmlOutput,
    sarifReport = sarifReport,
    sarifOutput = sarifOutput,
    abortOnError = abortOnError,
    absolutePaths = absolutePaths,
    noLines = noLines,
    quiet = quiet,
    checkAllWarnings = checkAllWarnings,
    ignoreWarnings = ignoreWarnings,
    warningsAsErrors = warningsAsErrors,
    showAll = showAll,
    explainIssues = explainIssues,
    checkReleaseBuilds = checkReleaseBuilds,
    checkTestSources = checkTestSources,
    ignoreTestSources = ignoreTestSources,
    ignoreTestFixturesSources = ignoreTestFixturesSources,
    checkGeneratedSources = checkGeneratedSources,
    checkDependencies = checkDependencies,
    baseline = baseline,
    targetSdk = targetSdk?.let { DefaultApiVersion(it).convert() }
        ?: targetSdkPreview?.let { DefaultApiVersion(it).convert() }
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

internal fun String.convertToExecution(): TestInfo.Execution? = when (this) {
    // The string coming from the old DSL (which is the enum converted) can be lowercase, so we
    // need to handle both
    "host","HOST" -> TestInfo.Execution.HOST
    "android_test_orchestrator","ANDROID_TEST_ORCHESTRATOR" -> TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR
    "androidx_test_orchestrator","ANDROIDX_TEST_ORCHESTRATOR" -> TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR
    else -> null
}
