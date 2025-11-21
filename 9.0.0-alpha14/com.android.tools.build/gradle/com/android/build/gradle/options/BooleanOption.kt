/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget.EXCLUDE_LIBRARIES_FROM_CONSTRAINTS
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget.VERSION_10_0
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget.VERSION_11_0
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget.VERSION_9_0
import com.android.build.gradle.options.Version.VERSION_3_5
import com.android.build.gradle.options.Version.VERSION_3_6
import com.android.build.gradle.options.Version.VERSION_4_0
import com.android.build.gradle.options.Version.VERSION_4_1
import com.android.build.gradle.options.Version.VERSION_4_2
import com.android.build.gradle.options.Version.VERSION_7_0
import com.android.build.gradle.options.Version.VERSION_7_2
import com.android.build.gradle.options.Version.VERSION_7_3
import com.android.build.gradle.options.Version.VERSION_BEFORE_4_0
import com.android.builder.model.AndroidProject
import com.android.builder.model.AndroidProject.PROPERTY_AVOID_TASK_REGISTRATION
import com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY
import com.android.builder.model.PROPERTY_BUILD_MODEL_V2_ONLY
import com.android.builder.model.PROPERTY_BUILD_WITH_STABLE_IDS
import com.android.builder.model.PROPERTY_DEPLOY_AS_INSTANT_APP
import com.android.builder.model.PROPERTY_EXTRACT_INSTANT_APK
import com.android.builder.model.PROPERTY_INVOKED_FROM_IDE
import com.android.builder.model.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL
import org.gradle.api.Project

enum class BooleanOption(
    override val propertyName: String,
    override val defaultValue: Boolean,
    val stage: Stage,
    futureStage: FutureStage? = null
) : Option<Boolean>, HasFutureStage {

    /* -----------
     * STABLE APIs
     */

    // IDE properties
    IDE_INVOKED_FROM_IDE(PROPERTY_INVOKED_FROM_IDE, false, ApiStage.Stable),
    IDE_BUILD_MODEL_ONLY_V2(PROPERTY_BUILD_MODEL_V2_ONLY, false, ApiStage.Stable),
    @Deprecated("This is for model v1 only. Please also use IDE_BUILD_MODEL_ONLY_V2")
    IDE_BUILD_MODEL_ONLY(PROPERTY_BUILD_MODEL_ONLY, false, ApiStage.Stable),
    @Deprecated("Use IDE_BUILD_MODEL_ONLY_V2")
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED, false, ApiStage.Stable),
    @Deprecated("Use IDE_BUILD_MODEL_ONLY_V2")
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, false, ApiStage.Stable),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, false, ApiStage.Stable),
    IDE_AVOID_TASK_REGISTRATION(PROPERTY_AVOID_TASK_REGISTRATION, false, ApiStage.Stable),
    //IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, false, ApiStage.Stable),

    // tell bundletool to only extract instant APKs.
    IDE_EXTRACT_INSTANT(PROPERTY_EXTRACT_INSTANT_APK, false, ApiStage.Stable),

    ENABLE_STUDIO_VERSION_CHECK("android.injected.studio.version.check", true, ApiStage.Stable),
    ENABLE_STABLE_IDS(PROPERTY_BUILD_WITH_STABLE_IDS, false, ApiStage.Stable),

    // Features' default values
    BUILD_FEATURE_DATABINDING("android.defaults.buildfeatures.databinding", false, ApiStage.Stable),

    BUILD_FEATURE_SHADERS(
        "android.defaults.buildfeatures.shaders",
        false,
        ApiStage.Stable,
    ),
    BUILD_FEATURE_VIEWBINDING("android.defaults.buildfeatures.viewbinding", false, ApiStage.Stable),
    BUILD_FEATURE_ANDROID_RESOURCES("android.library.defaults.buildfeatures.androidresources", true, ApiStage.Stable),

    // DSLs default values
    ENABLE_DATABINDING_KTX("android.defaults.databinding.addKtx", true, ApiStage.Stable),

    // AndroidX & Jetifier
    USE_ANDROID_X(
        "android.useAndroidX",
        true,
        ApiStage.Stable,
        FutureStage(
            true,
            ApiStage.Deprecated(DeprecationTarget.VERSION_11_0),
            Version.VERSION_10_0
        )
    ),
    ENABLE_JETIFIER("android.enableJetifier", false, ApiStage.Stable),

    DEBUG_OBSOLETE_API("android.debug.obsoleteApi", false, ApiStage.Stable),

    // Disabled by default due to low usage.
    GENERATE_MANIFEST_CLASS("android.generateManifestClass", false, ApiStage.Stable),

    NON_TRANSITIVE_R_CLASS("android.nonTransitiveRClass", true, ApiStage.Stable),

    /**
     * Setting this field to false indicates that in the current
     * project, all the APKs installed during test will be uninstalled
     * after test finishes. Setting it to true means that the AGP
     * will leave the test APKs untouched after test.
     *
     * Default is false
     */
    ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN("android.injected.androidTest.leaveApksInstalledAfterRun", false, ApiStage.Stable),

    /**
     * When this option is enabled, dexing transforms will use the full classpath (if desugaring
     * requires a classpath). This classpath consists of all external artifacts
     * ([com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL])
     * in addition to the input artifact's dependencies provided by Gradle through
     * [org.gradle.api.artifacts.transform.InputArtifactDependencies].
     *
     * This option is useful when some dependencies are missing from the input artifact's
     * dependencies (see bug 230454566), so the full classpath is needed.
     *
     * If dexing transforms do not require a classpath, this option is not used.
     */
    USE_FULL_CLASSPATH_FOR_DEXING_TRANSFORM(
        "android.useFullClasspathForDexingTransform",
        false,
        ApiStage.Stable
    ),

    PRINT_LINT_STACK_TRACE("android.lint.printStackTrace", false, ApiStage.Stable),

    PACKAGE_NATIVE_DEBUG_METADATA_IN_APP_BUNDLE(
            "android.bundle.includeNativeDebugMetadata",
            true,
            ApiStage.Stable
    ),

    JAVA_COMPILE_SUPPRESS_SOURCE_TARGET_DEPRECATION_WARNING(
        "android.javaCompile.suppressSourceTargetDeprecationWarning",
        false,
        ApiStage.Stable
    ),

    /**
     * Applies dependency constraints to align compile and runtime classpath.
     *
     * Apps always has their android test runtime classpath aligned to the main artifact's runtime classpath regardless of this flag.
     *
     * To disable all the alignments, see [DISABLE_ALL_CONSTRAINTS]
     *
     * Libraries has no alignment by default. See [EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS].
     */
    USE_DEPENDENCY_CONSTRAINTS("android.dependency.useConstraints", false, ApiStage.Stable),

    /**
     * This creates a sync issue when library constraints are applied, because disabling them would
     * result in a performance boost.
     */
    GENERATE_SYNC_ISSUE_WHEN_LIBRARY_CONSTRAINTS_ARE_ENABLED("android.generateSyncIssueWhenLibraryConstraintsAreEnabled", true, ApiStage.Stable),

    /* ------------------
     * SUPPORTED FEATURES
     */

    // Used by Studio as workaround for b/71054106, b/75955471
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true, FeatureStage.Supported),

    ENFORCE_UNIQUE_PACKAGE_NAMES(
        "android.uniquePackageNames",
        true,
        FeatureStage.Supported,
        FutureStage(
            true,
            FeatureStage.Enforced(Version.VERSION_10_0),
            Version.VERSION_10_0
        )),

    // Flag added to work around b/130596259.
    FORCE_JACOCO_OUT_OF_PROCESS("android.forceJacocoOutOfProcess", false, FeatureStage.Supported),

    PRECOMPILE_DEPENDENCIES_RESOURCES("android.precompileDependenciesResources", true, FeatureStage.Supported),

    INCLUDE_DEPENDENCY_INFO_IN_APKS("android.includeDependencyInfoInApks", true, FeatureStage.Supported),

    /**
     * Whether the legacy variant API (android.applicationVariants etc.) can be used a runtime.
     */
    ENABLE_LEGACY_VARIANT_API(
        "android.enableLegacyVariantApi", true, FeatureStage.Supported,
        FutureStage(false, FeatureStage.Enforced(Version.VERSION_10_0), Version.VERSION_10_0)
    ),

    /** Enables R8 strict full mode for keep rules (see [FULL_R8] for more context). */
    R8_STRICT_FULL_MODE_FOR_KEEP_RULES(
        "android.r8.strictFullModeForKeepRules",
        defaultValue = true,
        FeatureStage.Supported,
        FutureStage(
            true,
            FeatureStage.SoftlyEnforced(VERSION_11_0),
            Version.VERSION_10_0
        )
    ),

    /**
     * AGP 9.0 will only have unit tests for debug by default.
     */
    ONLY_ENABLE_UNIT_TEST_BY_DEFAULT_FOR_THE_TESTED_BUILD_TYPE(
        "android.onlyEnableUnitTestForTheTestedBuildType",
        true,
        FeatureStage.Supported
    ),

/**
     * When enabled, R8 will perform resource shrinking in a more optimal way.
     *
     * Note: This flag takes effect only if resource shrinking is enabled AND
     * [R8_INTEGRATED_RESOURCE_SHRINKING] is enabled AND [USE_NON_FINAL_RES_IDS] is enabled.
     */
    R8_OPTIMIZED_RESOURCE_SHRINKING(
        "android.r8.optimizedResourceShrinking",
        true,
        FeatureStage.Supported,
        FutureStage(
            true,
            FeatureStage.Enforced(Version.VERSION_10_0),
            Version.VERSION_10_0
        )
    ),
    /* -----------------
     * EXPERIMENTAL APIs
     */

    BUILD_FEATURE_MLMODELBINDING("android.defaults.buildfeatures.mlmodelbinding", false, ApiStage.Experimental),
    ENABLE_DEFAULT_DEBUG_SIGNING_CONFIG("android.experimental.useDefaultDebugSigningConfigForProfileableBuildtypes", false, ApiStage.Experimental),


    /* ---------------------
     * EXPERIMENTAL FEATURES
     */
    KMP_USE_JVM_PLATFORM_TYPE("android.kmp.use.jvm.platform.type", false, FeatureStage.Experimental),
    DISABLE_KMP_RUNTIME_CLASSPATH("android.kmp.disable.runtime.classpath", false, FeatureStage.Experimental),
    ENABLE_PROFILE_JSON("android.enableProfileJson", false, FeatureStage.Experimental),
    DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION("android.dependencyResolutionAtConfigurationTime.disallow", false, FeatureStage.Experimental),
    VERSION_CHECK_OVERRIDE_PROPERTY("android.overrideVersionCheck", false, FeatureStage.Experimental),
    OVERRIDE_PATH_CHECK_PROPERTY("android.overridePathCheck", false, FeatureStage.Experimental),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation", false, FeatureStage.Experimental),
    CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES("android.consumeDependenciesAsSharedLibraries", false, FeatureStage.Experimental),
    SUPPORT_OEM_TOKEN_LIBRARIES("android.enableOemTokenLibraries", false, FeatureStage.Experimental),
    DISABLE_EARLY_MANIFEST_PARSING("android.disableEarlyManifestParsing", false, FeatureStage.Experimental),
    CONDITIONAL_KEEP_RULES("android.useConditionalKeepRules", false, FeatureStage.Experimental),
    KEEP_SERVICES_BETWEEN_BUILDS("android.keepWorkerActionServicesBetweenBuilds", false, FeatureStage.Experimental),
    ENABLE_PARTIAL_R_INCREMENTAL_BUILDS("android.enablePartialRIncrementalBuilds", false, FeatureStage.Experimental),
    ENABLE_LOCAL_TESTING("android.bundletool.enableLocalTesting", false, FeatureStage.Experimental),
    DISABLE_MINSDKLIBRARY_CHECK("android.unsafe.disable.minSdkLibraryCheck", false, FeatureStage.Experimental),
    ENABLE_INSTRUMENTATION_TEST_DESUGARING("android.experimental.library.desugarAndroidTest", false, FeatureStage.Experimental),
    DISABLE_KOTLIN_ATTRIBUTE_SETUP("android.dependencyResolution.disable.kotlinPlatformTypeAttribute", false, FeatureStage.Experimental),
    /**
     * When enabled, incompatible APKs installed on a testing device will be uninstalled automatically
     * during an instrumentation test run (e.g. When INSTALL_FAILED_UPDATE_INCOMPATIBLE error happens
     * after attempting to install APKs for testing).
     */
    UNINSTALL_INCOMPATIBLE_APKS("android.experimental.testOptions.uninstallIncompatibleApks", false, FeatureStage.Experimental),

    /**
     * When enabled, "-show-kernel" and "-verbose" flags are used when running an Android emulator
     * for Gradle Managed devices.
     */
    GRADLE_MANAGED_DEVICE_EMULATOR_SHOW_KERNEL_LOGGING("android.experimental.testOptions.managedDevices.emulator.showKernelLogging", false, FeatureStage.Experimental),

    /**
     * Gradle Managed devices officially supports Android devices on API level 27 and higher because
     * using the API level 26 and lower devices increase instability. When a user tries to use those
     * old API devices, GMD task throws an exception and task fails by default.
     *
     * When this flag is enabled, it allows a user to use any old API level devices regardless of
     * its instability.
     */
    GRADLE_MANAGED_DEVICE_ALLOW_OLD_API_LEVEL_DEVICES("android.experimental.testOptions.managedDevices.allowOldApiLevelDevices", false, FeatureStage.Experimental),

    /** When set R classes are treated as compilation classpath in libraries, rather than runtime classpath, with values set to 0. */
    ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT("android.enableAdditionalTestOutput", true, FeatureStage.Experimental),

    ENABLE_EXTRACT_ANNOTATIONS("android.enableExtractAnnotations", true, FeatureStage.Experimental),

    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true, FeatureStage.Experimental),

    ENABLE_PARALLEL_NATIVE_JSON_GEN("android.enableParallelJsonGen", false, FeatureStage.Experimental),
    ENABLE_SIDE_BY_SIDE_CMAKE("android.enableSideBySideCmake", true, FeatureStage.Experimental),
    ENABLE_NATIVE_COMPILER_SETTINGS_CACHE("android.enableNativeCompilerSettingsCache", false, FeatureStage.Experimental),
    ENABLE_CMAKE_BUILD_COHABITATION("android.enableCmakeBuildCohabitation", false, FeatureStage.Experimental),
    ENABLE_PROGUARD_RULES_EXTRACTION("android.proguard.enableRulesExtraction", true, FeatureStage.Experimental),

    /**
     * Disables all constraints overriding all the other related flags.
     *
     * Intended use is to keep backwards compatibility for users who previously set android.dependency.useConstraints=false by turning this on.
     */
    DISABLE_ALL_CONSTRAINTS("android.dependency.disableAllConstraints", false, FeatureStage.Experimental),
    ENABLE_CLASSPATH_CHECK_TASKS("android.enableClasspathCheckTasks", false, FeatureStage.Experimental),
    ENABLE_DUPLICATE_CLASSES_CHECK("android.enableDuplicateClassesCheck", true, FeatureStage.Experimental),
    MINIMAL_KEEP_RULES("android.useMinimalKeepRules", true, FeatureStage.Experimental),
    EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES("android.bundle.excludeResSourcesForRelease", true, FeatureStage.Experimental),
    ENABLE_BUILD_CONFIG_AS_BYTECODE("android.enableBuildConfigAsBytecode", false, FeatureStage.Experimental),
    /** Whether lint should be run in process; the default is true. */
    RUN_LINT_IN_PROCESS("android.experimental.runLintInProcess", true, FeatureStage.Experimental),

    ENABLE_TEST_FIXTURES("android.experimental.enableTestFixtures", false, FeatureStage.Experimental),

    USE_DECLARATIVE_INTERFACES("android.experimental.declarative", false, FeatureStage.Experimental),

    /** Whether to force the APK to be deterministic. */
    FORCE_DETERMINISTIC_APK("android.experimental.forceDeterministicApk", false, FeatureStage.Experimental),

    /** Whether to skip apk generation via bundle if possible. */
    SKIP_APKS_VIA_BUNDLE_IF_POSSIBLE("android.experimental.skipApksViaBundleIfPossible", false, FeatureStage.Experimental),

    MISSING_LINT_BASELINE_IS_EMPTY_BASELINE(
        "android.experimental.lint.missingBaselineIsEmptyBaseline",
        false,
        FeatureStage.Experimental,
    ),

    LEGACY_TRANSFORM_TASK_FORCE_NON_INCREMENTAL(
            "android.experimental.legacyTransform.forceNonIncremental",
            false,
            FeatureStage.Experimental
    ),

    /** Force enables the identity transform for
     * - aar -> processed-aar
     * - jar -> processed-jar
     *
     * These should not be needed in most scenarios now, but keeping the option for backwards
     * compatibility.
     */
    ENABLE_IDENTITY_TRANSFORMS_FOR_PROCESSED_ARTIFACTS(
        "android.experimental.enableIdentityTransformsForProcessedArtifacts",
        false,
        FeatureStage.Experimental
    ),


    PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT("android.experimental.privacysandboxsdk.plugin.enable",
            false,
            FeatureStage.Experimental),

    PRIVACY_SANDBOX_SDK_SUPPORT("android.experimental.privacysandboxsdk.enable",
            false,
            FeatureStage.Experimental),

    PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES(
            "android.experimental.privacysandboxsdk.requireServices", true, FeatureStage.Experimental),

    VERIFY_AAR_CLASSES("android.experimental.verifyLibraryClasses", false, FeatureStage.Experimental),
    DISABLE_COMPILE_SDK_CHECKS("android.experimental.disableCompileSdkChecks", false, FeatureStage.Experimental),

    // Whether to suppress warnings about android:extractNativeLibs set to true in dependencies
    SUPPRESS_EXTRACT_NATIVE_LIBS_WARNINGS(
        "android.experimental.suppressExtractNativeLibsWarnings",
        false,
        FeatureStage.Experimental
    ),

    FUSED_LIBRARY_SUPPORT(
        "android.experimental.fusedLibrarySupport",
        false,
        FeatureStage.Experimental
    ),

    FUSED_LIBRARY_PUBLICATION_ONLY_MODE(
        "android.experimental.fusedLibrarySupport.publicationOnly",
        true,
        FeatureStage.Experimental
    ),

    /**
     * Whether to omit line numbers when writing lint baselines
     */
    LINT_BASELINE_OMIT_LINE_NUMBERS(
        "android.lint.baselineOmitLineNumbers",
        false,
        FeatureStage.Experimental
    ),

    ENABLE_NEW_TEST_DSL(
        "android.experimental.enableNewTestDsl",
        false,
        FeatureStage.Experimental
    ),

    @Suppress("unused")
    ENABLE_SCREENSHOT_TEST(
        "android.experimental.enableScreenshotTest",
        false,
        FeatureStage.Experimental
    ),

    /**
     * Whether to enable kotlin compilation for test fixtures
     */
    ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT(
        "android.experimental.enableTestFixturesKotlinSupport",
        false,
        FeatureStage.Experimental
    ),

    /**
     * Suppresses the warning shown when package attribute is present in the main manifest, and is
     * equal to the component's namespace.
     */
    SUPPRESS_MANIFEST_PACKAGE_WARNING(
        "android.experimental.suppressManifestPackageWarning",
        false,
        FeatureStage.Experimental
    ),

    /**
     * Whether to disable AGP's addition of the -Xuse-inline-scopes-numbers flag for KotlinCompile
     * tasks.
     */
    DISABLE_INLINE_SCOPES_NUMBERS(
        "android.kotlin.disableInlineScopesNumbers",
        false,
        FeatureStage.Experimental
    ),

    /**
     * Whether to enable the deviceTargetingConfig option in app bundles.
     */
    ENABLE_DEVICE_TARGETING_CONFIG_API(
        "android.experimental.enableDeviceTargetingConfigApi",
        false,
        FeatureStage.Experimental
    ),

    /**
     * Dump all artifacts locations in a json file in the variant build output folder.
     */
    DUMP_ARTIFACTS_LOCATIONS(
        "android.debug.dumpArtifactsLocations",
        defaultValue = false,
        FeatureStage.Experimental
    ),

    /**
     * Enables gradual R8 shrinking
     */
    GRADUAL_R8_SHRINKING(
        "android.experimental.gradual.r8",
        false,
        FeatureStage.Experimental
    ),

    ENABLE_PROBLEMS_API("android.enableProblemsAPI", false, FeatureStage.Experimental),

    // Flag should only be used in test.
    TEST_SIMULATE_AGP_VERSION_BEHAVIOR(
        "android.testSimulateAgpVersionBehavior",
        false,
        FeatureStage.Experimental,
        FutureStage(
            true,
            FeatureStage.Experimental,
            Version.VERSION_10_0
        )
    ),

    TEST_SUITE_SUPPORT(
        "android.experimental.testSuiteSupport",
        false,
        FeatureStage.Experimental,
        FutureStage(
            true,
            FeatureStage.Enforced(Version.VERSION_9_0),
            Version.VERSION_9_0
        )
    ),

    /**
     * Switches Android Test execution from the Unified Test Platform (UTP) to the experimental
     * direct-to-AGP implementation.
     */
    ANDROID_BUILTIN_TEST_PLATFORM(
        "android.experimental.androidTest.builtin_test_platform",
        false,
        FeatureStage.Experimental,
        futureStage = FutureStage(
            defaultValue = true,
            stage = FeatureStage.SoftlyEnforced(VERSION_10_0),
            version = Version.VERSION_9_0
        ),
    ),

    /**
     * When enabled, registers code coverage and test results aggregation tasks.
     */
    REPORT_AGGREGATION_SUPPORT(
        "android.experimental.reportAggregationSupport",
        false,
        FeatureStage.Experimental,
        futureStage = FutureStage(
            true,
            FeatureStage.Experimental,
            Version.VERSION_10_0
        )
    ),

    /**
     * Temporary workaround to continue using R8 param of --main-dex-list
     */
    R8_MAIN_DEX_LIST_DISALLOWED(
        "android.r8.mainDexList.disallowed",
        true,
        FeatureStage.Experimental,
        FutureStage(
            true,
            FeatureStage.Enforced(Version.VERSION_10_0),
            Version.VERSION_10_0
        )
    ),

    /** Enables R8 gradual support */
    R8_GRADUAL_API("android.r8.gradual.support", false, FeatureStage.Experimental),

    /* ------------------------
     * SOFTLY-ENFORCED FEATURES
     */
    /**
     * Setting custom shader path is required with `glslc.dir` property
     */
    CUSTOM_SHADER_PATH_REQUIRED(
        "android.custom.shader.path.required",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    ENABLE_RESOURCE_OPTIMIZATIONS(
        "android.enableResourceOptimizations",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    ENABLE_EMULATOR_CONTROL(
        "android.experimental.androidTest.enableEmulatorControl",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM(
        "android.experimental.androidTest.useUnifiedTestPlatform",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    DISABLE_MINIFY_LOCAL_DEPENDENCIES_FOR_LIBRARIES(
        "android.disableMinifyLocalDependenciesForLibraries",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    /**
     * Whether to do lint analysis per component (instead of analysing the main variant and the test
     * components in the same lint invocation).
     */
    LINT_ANALYSIS_PER_COMPONENT(
        "android.experimental.lint.analysisPerComponent",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    PRIVACY_SANDBOX_SDK_ENABLE_LINT(
        "android.experimental.privacysandboxsdk.enableLint",
        true,
        FeatureStage.SoftlyEnforced(VERSION_9_0)
    ),

    /**
     * When enabled, the <uses-sdk> tag in AndroidManifest.xml will generate build errors if it is
     * used to declare either minSdkVersion or targetSdkVersion. The only allowed use case
     * will be tools:overrideLibrary.
     */
    DISALLOW_USES_SDK_IN_MANIFEST(
        "android.usesSdkInManifest.disallowed",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET(
        "android.sdk.defaultTargetSdkToCompileSdkIfUnset",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    /**
     * Enables R8 full mode
     * (https://r8.googlesource.com/r8/+/refs/heads/8.8/compatibility-faq.md#r8-full-mode).
     *
     * Note that to help users migrate to R8 full mode, we provide 2 types of R8 full mode:
     *   - Legacy full mode for keep rules ([R8_STRICT_FULL_MODE_FOR_KEEP_RULES] = false): In this
     *   mode, the default constructor is implicitly kept when a class is kept
     *   (i.e., `-keep class A` is the same as `-keep class A { <init>(); }`)
     *   - Strict full mode for keep rules ([R8_STRICT_FULL_MODE_FOR_KEEP_RULES] = true): In this
     *   mode, the default constructor is not implicitly kept when a class is kept
     *   (i.e., `-keep class A` is different from `-keep class A { <init>(); }`).
     *
     * When migrating from legacy full mode to strict full mode, if the user's app or a library that
     * the app uses contains a keep rule such as `-keep class A`, then the app/library's author will
     * need to manually update the rule to `-keep class A { <init>(); }` if they want to keep
     * the default constructor. If they don't want to keep the default constructor, then they can
     * keep the rule as-is.
     */
    FULL_R8(
        "android.enableR8.fullMode",
        defaultValue = true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    FAIL_ON_MISSING_PROGUARD_FILES(
        "android.proguard.failOnMissingFiles",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    DEFAULT_MIN_COMPILE_SDK_IN_AAR_METADATA(
        "android.aar.metadata.defaultMinCompileSdkToCompileSdk",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    ENABLE_APP_COMPILE_TIME_R_CLASS(
        "android.enableAppCompileTimeRClass",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0),
    ),

    /**
     * Whether to enable built-in Kotlin support (https://issuetracker.google.com/259523353).
     *
     * When this property is enabled, AGP provides Kotlin support for all [Project]s without
     * requiring users to apply the `org.jetbrains.kotlin.android` plugin.
     *   - If the user applies the `org.jetbrains.kotlin.android` plugin, the build will fail as AGP
     *   already provides Kotlin support.
     *   - If the user applies the `com.android.built-in-kotlin` plugin, the build
     *   doesn't fail, but it also doesn't have any further effect.
     *
     * When this property is disabled, the users will need to apply either the
     * `com.android.built-in-kotlin` plugin or the `org.jetbrains.kotlin.android`
     * plugin to have Kotlin support.
     *   - If the user applies the `com.android.built-in-kotlin` plugin (recommended),
     *   AGP will provide Kotlin support for the current [Project] that the plugin is applied to.
     *   - If the user applies the `org.jetbrains.kotlin.android` plugin (legacy behavior), that
     *   plugin will provide Kotlin support for the current [Project] that the plugin is applied to.
     *   - If the user applies both plugins, the build will fail.
     */
    BUILT_IN_KOTLIN(
        propertyName = "android.builtInKotlin",
        defaultValue = true,
        stage = FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    /**
     * Expose only the new DSL.
     *
     * No longer use the legacy implementation classes to back the DSL.
     *
     * The legacy implementation classes also includes the legacy variant API, so setting this
     * subsumes setting `android.enableLegacyVariantApi`
     */
    USE_NEW_DSL("android.newDsl", true, FeatureStage.SoftlyEnforced(VERSION_10_0)),

    /**
     * Disallows users to pass Provider<*> instances to the Android sources set
     * APIs like srcDir and srcDirs.
     *
     * It is not supported by default because the old variant API keeps on resolving it too early,
     * and it is not possible for Android Studio to know if the directory contains generated or
     * static files which is important to make the files read-only or not.
     */
    DISALLOW_PROVIDER_IN_ANDROID_SOURCE_SET("android.sourceset.disallowProvider", true, FeatureStage.SoftlyEnforced(VERSION_10_0)),

    DEFAULT_ANDROIDX_TEST_RUNNER(
        propertyName = "android.default.androidx.test.runner",
        defaultValue = true,
        stage = FeatureStage.SoftlyEnforced(VERSION_9_0),
    ),

    /**
     * `getDefaultProguardRule(proguard-android.txt)` no longer supported in 9.0
     */
    R8_PROGUARD_ANDROID_TXT_DISALLOWED(
        "android.r8.proguardAndroidTxt.disallowed",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),

    /**
     * Global options such as `-dontoptimize` no longer supported in consumer rules in 9.0
     *
     * These options are only supported in app (base) modules.
     */
    R8_GLOBAL_OPTIONS_IN_CONSUMER_RULES_DISALLOWED(
        "android.r8.globalOptionsInConsumerRules.disallowed",
        true,
        FeatureStage.SoftlyEnforced(VERSION_10_0)
    ),


    /* -------------------
     * DEPRECATED API
    */

    // TODO(b/366029616) move to ApiStage.Removed
    BUILD_FEATURE_RESVALUES(
        "android.defaults.buildfeatures.resvalues",
        false,
        ApiStage.Deprecated(VERSION_10_0),
    ),

    // Flag used to indicate a "deploy as instant" run configuration.
    @Suppress("unused")
    IDE_DEPLOY_AS_INSTANT_APP(PROPERTY_DEPLOY_AS_INSTANT_APP, false, ApiStage.Deprecated(VERSION_9_0)),

    USE_NON_FINAL_RES_IDS("android.nonFinalResIds", true, ApiStage.Deprecated(VERSION_10_0)),

    /**
     * Controls whether libraries has the following constraints applied for classpaths:
     *   - compile -> runtime
     *   - androidTestRuntime -> runtime
     *
     * Only relevant when [USE_DEPENDENCY_CONSTRAINTS] is enabled.
     */
    EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS(
        "android.dependency.excludeLibraryComponentsFromConstraints",
        false,
        ApiStage.Deprecated(EXCLUDE_LIBRARIES_FROM_CONSTRAINTS),
    ),

    /* -------------------
     * DEPRECATED FEATURES
    */

    /** This flag is subsumed by android.enableLegacyVariantApi ([ENABLE_LEGACY_VARIANT_API]) */
    ENABLE_LEGACY_API(
        "android.compatibility.enableLegacyApi", true, FeatureStage.Deprecated(VERSION_10_0),
    ),

    /** This is subsumed by `android.newDsl` ([USE_NEW_DSL]) which also affects groovy scripts and plugins. */
    USE_NEW_DSL_INTERFACES("android.experimental.newDslInterfaces", false, FeatureStage.Deprecated(VERSION_10_0)),

    /* -----------------
     * ENFORCED FEATURES
     */
    @Suppress("unused")
    PREFER_CMAKE_FILE_API(
        "android.preferCmakeFileApi",
        true, FeatureStage.Enforced(VERSION_7_0)),

    @Suppress("unused")
    ENABLE_NATIVE_CONFIGURATION_FOLDING(
        "android.enableNativeConfigurationFolding",
        true,
        FeatureStage.Enforced(VERSION_7_0)),

    @Suppress("unused")
    ENABLE_SIDE_BY_SIDE_NDK(
        "android.enableSideBySideNdk",
        true,
        FeatureStage.Enforced(
            VERSION_4_1,
            "The android.enableSideBySideNdk property does not have any effect. " +
                    "Side-by-side NDK is always enabled."
        )
    ),

    @Suppress("unused")
    ENABLE_IMPROVED_DEPENDENCY_RESOLUTION(
        "android.enableImprovedDependenciesResolution",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "The android.enableImprovedDependenciesResolution property does not have any effect. "
                    + "Dependency resolution is only performed during task execution phase."
        )
    ),

    @Suppress("unused")
    ENABLE_NEW_RESOURCE_PROCESSING(
        "android.enableNewResourceProcessing",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "New resource processing is now always enabled."
        )
    ),

    @Suppress("unused")
    DISABLE_RES_MERGE_IN_LIBRARY(
        "android.disable.res.merge",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "Resources from dependencies are never merged in libraries."
        )
    ),

    @Suppress("unused")
    ENABLE_DAEMON_MODE_AAPT2(
        "android.enableAapt2DaemonMode",
        true,
        FeatureStage.Enforced(VERSION_BEFORE_4_0, "AAPT2 daemon mode is now always enabled.")
    ),

    @Suppress("unused")
    ENABLE_INCREMENTAL_DESUGARING(
        "android.enableIncrementalDesugaring",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect, incremental desugaring is always enabled."
        )
    ),

    @Suppress("unused")
    ENABLE_CORE_LAMBDA_STUBS(
        "android.enableCoreLambdaStubs",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect, core-lambda-stubs.jar is always in the bootclasspath."
        )
    ),

    @Suppress("unused")
    ENABLE_DEX_ARCHIVE(
        "android.useDexArchive",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect, incremental dexing is always used."
        )
    ),

    @Suppress("unused")
    ENABLE_AAPT2(
        "android.enableAapt2",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect, AAPT2 is now always used."
        )
    ),

    @Suppress("unused")
    USE_AAPT2_FROM_MAVEN(
        "android.useAapt2FromMaven",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect and AAPT2 from maven.google.com is now always used. "
                    + "If you wish to use a local executable of AAPT2 please use the "
                    + "'android.aapt2FromMavenOverride' option."
        )
    ),

    @Suppress("unused")
    ENABLE_D8_MAIN_DEX_LIST(
        "android.enableD8MainDexList",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "This property has no effect, D8 is always used to compute the main dex list."
        )
    ),

    @Suppress("unused")
    ENABLE_DATA_BINDING_V2(
        "android.databinding.enableV2",
        true,
        FeatureStage.Enforced(VERSION_BEFORE_4_0, "Databinding v1 is removed.")
    ),

    ENABLE_SEPARATE_R_CLASS_COMPILATION(
        "android.enableSeparateRClassCompilation",
        true,
        FeatureStage.Enforced(
            VERSION_BEFORE_4_0,
            "Separate R class compilation has been enabled and can no longer be disabled."
        )
    ),

    @Suppress("unused")
    ENABLE_GRADLE_WORKERS(
        "android.enableGradleWorkers",
        true,
        FeatureStage.Enforced(
            VERSION_4_2,
            "Gradle workers are always used."
        )
    ),

    @Suppress("unused")
    ENABLE_D8(
        "android.enableD8",
        true,
        FeatureStage.Enforced(
            VERSION_7_0,
            "For more details, see https://d.android.com/r/studio-ui/d8-overview.html."
        )
    ),

    @Suppress("unused")
    ENABLE_D8_DESUGARING(
        "android.enableD8.desugaring",
        true,
        FeatureStage.Enforced(
            VERSION_7_0,
            "D8 desugaring is used by default, when applicable."
        )
    ),

    @Suppress("unused")
    ENABLE_R8_DESUGARING(
        "android.enableR8.desugaring",
        true,
        FeatureStage.Enforced(
            VERSION_7_0,
            "R8 desugaring is used by default, when applicable."
        )
    ),

    USE_NEW_LINT_MODEL("android.experimental.useNewLintModel", true, FeatureStage.Enforced(VERSION_7_0)),

    /** Whether Jetifier will skip libraries that already support AndroidX. */
    JETIFIER_SKIP_IF_POSSIBLE("android.jetifier.skipIfPossible", true, FeatureStage.Enforced(VERSION_7_0)),

    @Suppress("unused")
    NON_TRANSITIVE_APP_R_CLASS(
            "android.experimental.nonTransitiveAppRClass",
            true,
            FeatureStage.Enforced(
                    VERSION_7_0,
                    "Non-transitive R classes are now enabled in app modules with the " +
                            "${NON_TRANSITIVE_R_CLASS.propertyName} option.")),

    /** Incremental dexing task using D8's new API for desugaring graph computation. */
    ENABLE_INCREMENTAL_DEXING_TASK_V2("android.enableIncrementalDexingTaskV2", true, FeatureStage.Enforced(VERSION_7_0)),

    /** Incremental dexing transform. */
    ENABLE_INCREMENTAL_DEXING_TRANSFORM("android.enableIncrementalDexingTransform", true, FeatureStage.Enforced(VERSION_7_0)),

    ENABLE_R8_LIBRARIES("android.enableR8.libraries", true, FeatureStage.Enforced(VERSION_7_0)),

    ENABLE_SYMBOL_TABLE_CACHING("android.enableSymbolTableCaching", true, FeatureStage.Enforced(VERSION_7_0)),

    ENABLE_JVM_RESOURCE_COMPILER("android.enableJvmResourceCompiler", true, FeatureStage.Enforced(VERSION_7_0)),

    /** Whether to use lint's partial analysis functionality. */
    USE_LINT_PARTIAL_ANALYSIS("android.enableParallelLint", true, FeatureStage.Enforced(VERSION_7_2)),

    ENABLE_AAPT2_WORKER_ACTIONS(
        "android.enableAapt2WorkerActions",
        true,
        FeatureStage.Enforced(
            VERSION_7_3,
            "AAPT2 worker actions have been used unconditionally since Android Gradle Plugin 3.3"
        )
    ),
    ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION(
        "android.enableJacocoTransformInstrumentation",
        true,
        FeatureStage.Enforced(VERSION_7_3)
    ),

    @Suppress("unused")
    ENABLE_SOURCE_SET_PATHS_MAP(
            "android.enableSourceSetPathsMap",
            true,
            FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    @Suppress("unused")
    RELATIVE_COMPILE_LIB_RESOURCES(
            "android.cacheCompileLibResources",
            true,
            FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    @Suppress("unused")
    R8_FAIL_ON_MISSING_CLASSES(
        "android.r8.failOnMissingClasses",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    USE_NEW_JAR_CREATOR(
        "android.useNewJarCreator",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    USE_NEW_APK_CREATOR(
        "android.useNewApkCreator",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    WARN_ABOUT_DEPENDENCY_RESOLUTION_AT_CONFIGURATION(
        "android.dependencyResolutionAtConfigurationTime.warn",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    ENABLE_ART_PROFILES(
        "android.enableArtProfiles",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    USE_RELATIVE_PATH_IN_TEST_CONFIG(
        "android.testConfig.useRelativePath",
        true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    INCLUDE_REPOSITORIES_IN_DEPENDENCY_REPORT(
    "android.bundletool.includeRepositoriesInDependencyReport",
    true,
        FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    ENABLE_INCREMENTAL_DATA_BINDING(
        "android.databinding.incremental",
        true,
        FeatureStage.Enforced(Version.VERSION_8_1)
    ),

    ENABLE_NEW_RESOURCE_SHRINKER("android.enableNewResourceShrinker",
            true,
            FeatureStage.Enforced(Version.VERSION_8_0)
    ),

    @Suppress("unused")
    ENABLE_R_TXT_RESOURCE_SHRINKING(
            "android.enableRTxtResourceShrinking",
            true,
            FeatureStage.Enforced(Version.VERSION_8_1)
    ),

    @Suppress("unused")
    COMPILE_CLASSPATH_LIBRARY_R_CLASSES(
            "android.useCompileClasspathLibraryRClasses",
            true,
            FeatureStage.Enforced(Version.VERSION_8_1)
    ),

    ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE(
        "android.bundle.enableUncompressedNativeLibs",
        true,
        FeatureStage.Enforced(Version.VERSION_8_1)
    ),

    ENABLE_GLOBAL_SYNTHETICS(
        "android.enableGlobalSyntheticsGeneration",
        true,
        FeatureStage.Enforced(Version.VERSION_8_1)
    ),

    ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM(
        "android.enableDexingArtifactTransform.desugaring",
        true,
        FeatureStage.Enforced(
            Version.VERSION_8_2,
            "If you run into issues with dexing transforms, try setting `${USE_FULL_CLASSPATH_FOR_DEXING_TRANSFORM.propertyName} = true` instead."
        )
    ),

    ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS(
        "android.enableDexingArtifactTransformForExternalLibs",
        true,
        FeatureStage.Enforced(Version.VERSION_8_2)
    ),

    ENABLE_DEXING_ARTIFACT_TRANSFORM(
        "android.enableDexingArtifactTransform",
        true,
        FeatureStage.Enforced(
            Version.VERSION_8_3,
            "If you run into issues with dexing transforms, try setting `${USE_FULL_CLASSPATH_FOR_DEXING_TRANSFORM.propertyName} = true` instead."
        )
    ),

    R8_INTEGRATED_RESOURCE_SHRINKING(
        "android.r8.integratedResourceShrinking",
        true,
        FeatureStage.Enforced(Version.VERSION_9_0,
            additionalMessage = "The android.r8.integratedResourceShrinking property does not have any effect. " +
                    "R8 Integrated Resource Shrinking is always enabled.")
    ),

    ENABLE_NEW_RESOURCE_SHRINKER_PRECISE(
        "android.enableNewResourceShrinker.preciseShrinking", true, FeatureStage.Enforced(
            Version.VERSION_9_0,
            additionalMessage = "Precise shrinking is always enabled and this property no longer has any effect."
        )
    ),

    /**
     * When enabled, Gradle Managed Device allows a custom managed device type that can be provided
     * by a plugin by implementing ManagedDeviceTestRunner APIs.
     */
    GRADLE_MANAGED_DEVICE_CUSTOM_DEVICE(
        "android.experimental.testOptions.managedDevices.customDevice",
        true,
        FeatureStage.Enforced(Version.VERSION_9_0)
    ),


    /* ----------------
     * REMOVED API
     */

    ENABLE_RESOURCE_NAMESPACING_DEFAULT(
        "android.enableResourceNamespacingDefault",
        false,
        ApiStage.Removed(
            Version.VERSION_9_0,
            "The android.enableResourceNamespacingDefault property has no effect"
        )
    ),
    // flag does not work without namespace that's been removed
    CONVERT_NON_NAMESPACED_DEPENDENCIES(
        "android.convertNonNamespacedDependencies",
        false,
        ApiStage.Removed(
            Version.VERSION_9_0,
            "The android.convertNonNamespacedDependencies property has no effect"
        )
    ),

    BUILD_FEATURE_RENDERSCRIPT(
        "android.defaults.buildfeatures.renderscript",
        false,
        ApiStage.Removed(
            Version.VERSION_9_0,
            "The buildfeatures.renderscript property has no effect"
        ),
    ),

    BUILD_FEATURE_AIDL(
        "android.defaults.buildfeatures.aidl",
        false,
        ApiStage.Removed(
            Version.VERSION_9_0,
            "The buildfeatures.aidl property has no effect"
        ),
    ),

    /* ----------------
     * REMOVED FEATURES
     */

    @Suppress("unused")
    BUILD_FEATURE_BUILDCONFIG(
        "android.defaults.buildfeatures.buildconfig",
        false,
        ApiStage.Removed(Version.VERSION_9_0),
    ),

    @Suppress("unused")
    ENABLE_IN_PROCESS_AAPT2(
        "android.enableAapt2jni",
        false,
        FeatureStage.Removed(VERSION_BEFORE_4_0, "AAPT2 JNI has been removed.")
    ),

    @Suppress("unused")
    ENABLE_DEPRECATED_NDK(
        "android.useDeprecatedNdk",
        false,
        FeatureStage.Removed(VERSION_BEFORE_4_0, "NdkCompile is no longer supported")
    ),

    @Suppress("unused")
    INJECT_SDK_MAVEN_REPOS(
        "android.injectSdkMavenRepos",
        false,
        FeatureStage.Removed(
            VERSION_3_5,
            "The ability to inject the Android SDK maven repos is removed in AGP 3.5"
        )
    ),

    @Suppress("unused")
    ENABLE_UNIT_TEST_BINARY_RESOURCES(
        "android.enableUnitTestBinaryResources",
        false,
        FeatureStage.Removed(
            VERSION_BEFORE_4_0,
            "The raw resource for unit test functionality is removed."
        )
    ),

    @Suppress("unused")
    ENABLE_EXPERIMENTAL_FEATURE_DATABINDING(
        "android.enableExperimentalFeatureDatabinding",
        false,
        FeatureStage.Removed(
            VERSION_4_0,
            "This property has no effect. The features plugin was removed in AGP 4.0.")
    ),

    @Suppress("unused")
    ENABLE_SEPARATE_APK_RESOURCES(
        "android.enableSeparateApkRes",
        false,
        FeatureStage.Removed(VERSION_BEFORE_4_0, "Instant run is replaced by apply changes.")
    ),

    @Suppress("unused")
    KEEP_TIMESTAMPS_IN_APK(
        "android.keepTimestampsInApk",
        false,
        FeatureStage.Removed(
            VERSION_3_6,
            "The ability to keep timestamps in the APK is removed in AGP 3.6"
        )
    ),

    @Suppress("unused")
    ENABLE_SEPARATE_ANNOTATION_PROCESSING(
        "android.enableSeparateAnnotationProcessing",
        false,
        FeatureStage.Removed(VERSION_4_0, "This feature was removed in AGP 4.0")
    ),

    @Suppress("unused")
    GENERATE_R_JAVA(
        "android.generateRJava",
        false,
        FeatureStage.Removed(VERSION_4_1, "This feature was removed in AGP 4.1")),

    @Suppress("unused")
    ENABLE_BUILD_CACHE(
        "android.enableBuildCache",
        false,
        FeatureStage.Removed(VERSION_7_0, "The Android-specific build caches were superseded by the Gradle build cache (https://docs.gradle.org/current/userguide/build_cache.html).")
    ),

    @Suppress("unused")
    ENABLE_INTERMEDIATE_ARTIFACTS_CACHE(
        "android.enableIntermediateArtifactsCache",
        false,
        FeatureStage.Removed(VERSION_7_0, "The Android-specific build caches were superseded by the Gradle build cache (https://docs.gradle.org/current/userguide/build_cache.html).")
    ),

    @Suppress("unused")
    ENABLE_DESUGAR(
            "android.enableDesugar",
            false,
            FeatureStage.Removed(VERSION_7_0, "Desugar tool has been removed from AGP.")
    ),

    @Suppress("unused")
    ENABLE_TEST_SHARDING("android.androidTest.shardBetweenDevices", false, FeatureStage.Removed(Version.VERSION_8_2, "Cross device sharding is no longer supported.")),

    @Suppress("unused")
    ENABLE_VCS_INFO("android.enableVcsInfo", false, FeatureStage.Removed(Version.VERSION_8_3, "This feature is now enabled in the DSL per build type with \"vcsInfo.include = true\".")),

    @Suppress("unused")
    ADDITIONAL_ARTIFACTS_IN_MODEL("android.experimental.additionalArtifactsInModel", false, FeatureStage.Removed(Version.VERSION_8_11, "Android Studio is responsible for managing multi-variant Javadoc/source support.")),

    /**
     * Note: Use [R8_OPTIMIZED_RESOURCE_SHRINKING] instead.
     */
    R8_OPTIMIZED_SHRINKING(
        "android.r8.optimizedShrinking",
        false,
        FeatureStage.Removed(Version.VERSION_8_11)
    ),


    /**
     * When enabled, Gradle Managed Device test results will be included in the mergeAndroidReports task from the
     * android-reporting plugin.
     *
     * This will cause all managed devices to run for all variants in all subprojects when the mergeAndroidReports
     * task is executed.
     */
    @Suppress("unused")
    GRADLE_MANAGED_DEVICE_INCLUDE_MANAGED_DEVICES_IN_REPORTING(
        "android.experimental.testOptions.managedDevices.includeInMergedReport",
        false,
        FeatureStage.Removed(
            Version.VERSION_9_0,
            "The managedDevices.includeInMergedReport property has no effect"
        )
    ),

    ; // end of enums

    override val status = stage.status

    override val futureStage: FutureStage? = when (stage) {
        is FeatureStage.SoftlyEnforced -> {
            check(futureStage == null) {
                "Do not set ${FutureStage::class.simpleName} for property '$propertyName' manually" +
                        " because it has stage ${FeatureStage.SoftlyEnforced::class.simpleName}" +
                        " which already contains the necessary information to infer its ${FutureStage::class.simpleName}."
            }
            FutureStage(
                defaultValue = true,
                stage = FeatureStage.Enforced(enforcedVersion = stage.enforcementTarget.removalTarget),
                version = stage.enforcementTarget.removalTarget
            )
        }
        is FeatureStage.Deprecated -> {
            check(futureStage == null) {
                "Do not set ${FutureStage::class.simpleName} for property '$propertyName' manually" +
                        " because it has stage ${FeatureStage.Deprecated::class.simpleName}" +
                        " which already contains the necessary information to infer its ${FutureStage::class.simpleName}."
            }
            FutureStage(
                defaultValue = false,
                stage = FeatureStage.Removed(removedVersion = stage.removalTarget.removalTarget),
                version = stage.removalTarget.removalTarget
            )
        }
        is ApiStage.Deprecated -> {
            check(futureStage == null) {
                "Do not set ${FutureStage::class.simpleName} for property '$propertyName' manually" +
                        " because it has stage ${ApiStage.Deprecated::class.simpleName}" +
                        " which already contains the necessary information to infer its ${FutureStage::class.simpleName}."
            }
            FutureStage(
                defaultValue = false,
                stage = ApiStage.Removed(removedVersion = stage.removalTarget.removalTarget),
                version = stage.removalTarget.removalTarget
            )
        }
        is FeatureStage.Enforced, is FeatureStage.Removed, is ApiStage.Removed -> {
            check(futureStage == null) {
                "Do not set ${FutureStage::class.simpleName} for property '$propertyName'" +
                        " because it has stage ${stage.javaClass.simpleName}."
            }
            null
        }
        else -> futureStage
    }

    override fun parse(value: Any): Boolean {
        return parseBoolean(propertyName, value)
    }
}
