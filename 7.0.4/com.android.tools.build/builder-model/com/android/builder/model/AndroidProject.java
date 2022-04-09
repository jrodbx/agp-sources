/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Entry point for the model of the Android Projects. This models a single module, whether the
 * module is an app project, a library project, a Instant App feature project, an instantApp bundle
 * project, or a dynamic feature split project.
 */
public interface AndroidProject {
    //  Injectable properties to use with -P
    // Sent by Studio 1.0 ONLY
    String PROPERTY_BUILD_MODEL_ONLY = "android.injected.build.model.only";
    // Sent by Studio 1.1+
    String PROPERTY_BUILD_MODEL_ONLY_ADVANCED = "android.injected.build.model.only.advanced";
    // Sent by Studio 2.4+. The value of the prop is a monotonically increasing integer.
    // see MODEL_LEVEL_* constants
    String PROPERTY_BUILD_MODEL_ONLY_VERSIONED = "android.injected.build.model.only.versioned";
    // Sent by Studio 2.4+. Additional model feature trigger on a case by case basis
    // Value is simply true to enable.
    String PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES = "android.injected.build.model.feature.full.dependencies";
    /**
     * Was a property to disable source download during model sync, which is no longer done by AGP.
     *
     * @deprecated Only has effect in AGP 3.5. Android Studio 3.6 onwards downloads sources using an
     *     injected gradle tooling model and model builder.
     */
    @Deprecated
    String PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD =
            "android.injected.build.model.disable.src.download";

    // Sent by Studio 2.2+ and Android Support plugin running with IDEA from 4.1+
    // This property will enable compatibility checks between Android Support plugin and the Android
    // Gradle plugin.
    // A use case for this property is that by restricting which versions are compatible
    // with the plugin, we could safely remove deprecated methods in the builder-model interfaces.
    String PROPERTY_ANDROID_SUPPORT_VERSION = "android.injected.studio.version";

    // Sent in when external native projects models requires a refresh.
    String PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL = "android.injected.refresh.external.native.model";

    // Sent by Studio 2.2+
    // This property is sent when a run or debug is invoked.  APK built with this property should
    // be marked with android:testOnly="true" in the AndroidManifest.xml such that it will be
    // rejected by the Play store.
    String PROPERTY_TEST_ONLY = "android.injected.testOnly";

    // Sent by Studio 1.5+

    // The version api level of the target device.
    String PROPERTY_BUILD_API = "android.injected.build.api";
    // The version codename of the target device. Null for released versions,
    String PROPERTY_BUILD_API_CODENAME = "android.injected.build.codename";

    String PROPERTY_BUILD_ABI = "android.injected.build.abi";
    String PROPERTY_BUILD_DENSITY = "android.injected.build.density";

    // Has the effect of telling the Gradle plugin to
    //   1) Generate machine-readable errors
    //   2) Generate build metadata JSON files
    String PROPERTY_INVOKED_FROM_IDE = "android.injected.invoked.from.ide";

    // deprecated. Kept here so that newew Studio can still inject it for older plugin
    // but newer plugin don't do anything different based on this property.
    @SuppressWarnings("unused")
    String PROPERTY_GENERATE_SOURCES_ONLY = "android.injected.generateSourcesOnly";

    String PROPERTY_SIGNING_STORE_FILE = "android.injected.signing.store.file";
    String PROPERTY_SIGNING_STORE_PASSWORD = "android.injected.signing.store.password";
    String PROPERTY_SIGNING_KEY_ALIAS = "android.injected.signing.key.alias";
    String PROPERTY_SIGNING_KEY_PASSWORD = "android.injected.signing.key.password";
    String PROPERTY_SIGNING_STORE_TYPE = "android.injected.signing.store.type";
    String PROPERTY_SIGNING_V1_ENABLED = "android.injected.signing.v1-enabled";
    String PROPERTY_SIGNING_V2_ENABLED = "android.injected.signing.v2-enabled";

    String PROPERTY_DEPLOY_AS_INSTANT_APP = "android.injected.deploy.instant-app";

    String PROPERTY_BUILD_WITH_STABLE_IDS = "android.injected.enableStableIds";

    String PROPERTY_SIGNING_COLDSWAP_MODE = "android.injected.coldswap.mode";

    String PROPERTY_APK_SELECT_CONFIG = "android.inject.apkselect.config";

    String PROPERTY_EXTRACT_INSTANT_APK = "android.inject.bundle.extractinstant";

    /** Version code to be used in the built APK. */
    String PROPERTY_VERSION_CODE = "android.injected.version.code";

    /** Version code injected by Android Studio when using Instant Run. */
    int INSTANT_RUN_VERSION_CODE = Integer.MAX_VALUE;

    /** Version name to be used in the built APK. */
    String PROPERTY_VERSION_NAME = "android.injected.version.name";

    /** Version name injected by Android Studio when using Instant Run. */
    String INSTANT_RUN_VERSION_NAME = "INSTANT_RUN";

    /**
     * Comma-separated list of {@link OptionalCompilationStep} value names, used with Instant Run.
     */
    String PROPERTY_OPTIONAL_COMPILATION_STEPS = "android.optional.compilation";

    /**
     * Location for APKs. If defined as a relative path, then it is resolved against the
     * project's path.
     */
    String PROPERTY_APK_LOCATION = "android.injected.apk.location";

    /**
     * Location of the build attribution file produced by the gradle plugin to be deserialized and
     * used in the IDE build attribution.
     */
    String PROPERTY_ATTRIBUTION_FILE_LOCATION = "android.injected.attribution.file.location";

    /**
     * Comma separated list of on-demand dynamic modules or instant app modules names that are
     * selected by the user for installation on the device during deployment.
     */
    String PROPERTY_INJECTED_DYNAMIC_MODULES_LIST = "android.injected.modules.install.list";

    /** Logging level of native build output. Possible values are "quiet" and "verbose". */
    String PROPERTY_NATIVE_BUILD_OUTPUT_LEVEL = "android.native.buildOutput";

    String ARTIFACT_MAIN = "_main_";
    String ARTIFACT_ANDROID_TEST = "_android_test_";
    String ARTIFACT_UNIT_TEST = "_unit_test_";

    String FD_INTERMEDIATES = "intermediates";
    String FD_LOGS = "logs";
    String FD_OUTPUTS = "outputs";
    String FD_GENERATED = "generated";

    int GENERATION_ORIGINAL = 1;
    int GENERATION_COMPONENT = 2; // component plugin is not supported since 3.5

    int MODEL_LEVEL_0_ORIGINAL = 0 ; // studio 1.0, no support for SyncIssue
    int MODEL_LEVEL_1_SYNC_ISSUE = 1; // studio 1.1+, with SyncIssue
    //int MODEL_LEVEL_2_DONT_USE = 2; // Don't use this. Go level 1 to level 3 when ready.
    int MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD =
            3; // Model for 3.0 with no variant output in import sync model.
    int MODEL_LEVEL_4_NEW_DEP_MODEL = 4;
    int MODEL_LEVEL_LATEST = MODEL_LEVEL_4_NEW_DEP_MODEL;

    /**
     * Returns the model version. This is a string in the format X.Y.Z
     *
     * @return a string containing the model version.
     */
    @NonNull
    String getModelVersion();

    /**
     * Returns the model api version.
     * <p>
     * This is different from {@link #getModelVersion()} in a way that new model
     * version might increment model version but keep existing api. That means that
     * code which was built against particular 'api version' might be safely re-used for all
     * new model versions as long as they don't change the api.
     * <p>
     * Every new model version is assumed to return an 'api version' value which
     * is equal or greater than the value used by the previous model version.
     *
     * @return model's api version
     */
    int getApiVersion();

    /**
     * Returns the name of the module.
     *
     * @return the name of the module.
     */
    @NonNull
    String getName();

    /**
     * Returns whether this is a library.
     * @return true for a library module.
     * @deprecated use {@link #getProjectType()} instead.
     */
    @Deprecated
    boolean isLibrary();

    /**
     * Returns the type of project: Android application, library, feature, instantApp.
     *
     * @return the type of project.
     * @since 2.3
     */
    int getProjectType();

    /**
     * Returns the optional group-id of the artifact represented by this project.
     *
     * @since 3.6
     */
    @Nullable
    String getGroupId();

    /**
     * Returns the namespace of the main artifact.
     *
     * <p>This is here rather than on {@link AndroidArtifact} because this is common to all
     * artifacts as it cannot be changed per variants.
     */
    @NonNull
    String getNamespace();

    /**
     * Returns the namespace of the AndroidTest artifact.
     *
     * <p>This is here rather than on {@link AndroidArtifact} because this is common to all
     * artifacts as it cannot be changed per variants.
     *
     * <p>If there are no AndroidTest components, this returns null.
     */
    @Nullable
    String getAndroidTestNamespace();

    /**
     * Returns the {@link ProductFlavorContainer} for the 'main' default config.
     *
     * @return the product flavor.
     */
    @NonNull
    ProductFlavorContainer getDefaultConfig();

    /**
     * Returns a list of all the {@link BuildType} in their container.
     *
     * @return a list of build type containers.
     */
    @NonNull
    Collection<BuildTypeContainer> getBuildTypes();

    /**
     * Returns a list of all the {@link ProductFlavor} in their container.
     *
     * @return a list of product flavor containers.
     */
    @NonNull
    Collection<ProductFlavorContainer> getProductFlavors();

    /**
     * Returns a list of all the variants.
     *
     * This does not include test variant. Test variants are additional artifacts in their
     * respective variant info.
     *
     * @return a list of the variants.
     */
    @NonNull
    Collection<Variant> getVariants();

    /**
     * Returns a list of all the variant names.
     *
     * <p>This does not include test variant. Test variants are additional artifacts in their
     * respective variant info.
     *
     * @return a list of all the variant names.
     * @since 3.2.
     */
    @NonNull
    Collection<String> getVariantNames();

    /**
     * Returns the name of the variant the IDE should use when opening the project for the first
     * time.
     *
     * @return the name of a variant that exists under the presence of the variant filter. Only
     *     returns null if all variants are removed.
     * @since 3.5
     */
    @Nullable
    String getDefaultVariant();

    /**
     * Returns a list of all the flavor dimensions, may be empty.
     *
     * @return a list of the flavor dimensions.
     */
    @NonNull
    Collection<String> getFlavorDimensions();

    /**
     * Returns a list of extra artifacts meta data. This does not include the main artifact.
     *
     * @return a list of extra artifacts
     */
    @NonNull
    Collection<ArtifactMetaData> getExtraArtifacts();

    /**
     * Returns the compilation target as a string. This is the full extended target hash string.
     * (see com.android.sdklib.IAndroidTarget#hashString())
     *
     * @return the target hash string
     */
    @NonNull
    String getCompileTarget();

    /**
     * Returns the boot classpath matching the compile target. This is typically android.jar plus
     * other optional libraries.
     *
     * @return a list of jar files.
     */
    @NonNull
    Collection<String> getBootClasspath();

    /**
     * Returns a list of folders or jar files that contains the framework source code.
     */
    @NonNull
    Collection<File> getFrameworkSources();

    /**
     * Returns the collection of toolchains used to create any native libraries.
     *
     * @return collection of toolchains.
     */
    @NonNull
    Collection<NativeToolchain> getNativeToolchains();

    /**
     * Returns a list of {@link SigningConfig}.
     */
    @NonNull
    Collection<SigningConfig> getSigningConfigs();

    /**
     * Returns the aapt options.
     */
    @NonNull
    AaptOptions getAaptOptions();

    /**
     * Returns the lint options.
     */
    @NonNull
    LintOptions getLintOptions();

    /**
     * Returns the dependencies that were not successfully resolved. The returned list gets
     * populated only if the system property {@link #PROPERTY_BUILD_MODEL_ONLY} has been
     * set to {@code true}.
     * <p>
     * Each value of the collection has the format group:name:version, for example:
     * com.google.guava:guava:15.0.2
     *
     * @return the dependencies that were not successfully resolved.
     * @deprecated use {@link #getSyncIssues()}
     */
    @Deprecated
    @NonNull
    Collection<String> getUnresolvedDependencies();

    /**
     * Returns issues found during sync.  The returned list gets
     * populated only if the system property {@link #PROPERTY_BUILD_MODEL_ONLY} has been
     * set to {@code true}.
     *
     * @deprecated request {@link ProjectSyncIssues} instead.
     */
    @Deprecated
    @NonNull
    Collection<SyncIssue> getSyncIssues();

    /**
     * Returns the compile options for Java code.
     */
    @NonNull
    JavaCompileOptions getJavaCompileOptions();

    /**
     * Returns the build folder of this project.
     */
    @NonNull
    File getBuildFolder();

    /**
     * Returns the resource prefix to use, if any. This is an optional prefix which can
     * be set and which is used by the defaults to automatically choose new resources
     * with a certain prefix, warn if resources are not using the given prefix, etc.
     * This helps work with resources in the app namespace where there could otherwise
     * be unintentional duplicated resource names between unrelated libraries.
     *
     * @return the optional resource prefix, or null if not set
     */
    @Nullable
    String getResourcePrefix();

    /**
     * Returns the build tools version used by this module.
     * @return the build tools version.
     */
    @NonNull
    String getBuildToolsVersion();

    /**
     * Returns the NDK version used by this module.
     *
     * @return the NDK version.
     */
    @NonNull
    String getNdkVersion();

    /**
     * Returns the generation of the plugin.
     *
     * <p>1 is original plugin, 2 is component based plugin (AKA experimental, not used anymore)
     *
     * @return the generation value
     */
    int getPluginGeneration();

    /**
     * Returns true if this is the base feature split.
     *
     * @return true if this is the base feature split
     * @since 2.4
     */
    boolean isBaseSplit();

    /**
     * Returns the list of dynamic features.
     *
     * <p>The values are Gradle path. Only valid for base splits.
     *
     * @return
     */
    @NonNull
    Collection<String> getDynamicFeatures();

    /** Returns the options for view binding. */
    @NonNull
    ViewBindingOptions getViewBindingOptions();

    @Nullable
    DependenciesInfo getDependenciesInfo();

    /** Returns the AGP flags for this project. */
    @NonNull
    AndroidGradlePluginProjectFlags getFlags();

    /**
     * Returns the minimal information of variants for this project, excluding test related
     * variants.
     *
     * @since 4.1
     */
    @NonNull
    Collection<VariantBuildInformation> getVariantsBuildInformation();

    /** Returns the lint jars that this module uses to run extra lint checks */
    @NonNull
    List<File> getLintRuleJars();
}
