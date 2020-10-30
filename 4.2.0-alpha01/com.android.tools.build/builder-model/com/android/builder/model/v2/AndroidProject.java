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

package com.android.builder.model.v2;

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

    // Sent by Studio 4.2+
    String PROPERTY_BUILD_MODEL_ONLY = "android.injected.build.model.v2";

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

    String PROPERTY_SIGNING_STORE_FILE = "android.injected.signing.store.file";
    String PROPERTY_SIGNING_STORE_PASSWORD = "android.injected.signing.store.password";
    String PROPERTY_SIGNING_KEY_ALIAS = "android.injected.signing.key.alias";
    String PROPERTY_SIGNING_KEY_PASSWORD = "android.injected.signing.key.password";
    String PROPERTY_SIGNING_STORE_TYPE = "android.injected.signing.store.type";
    String PROPERTY_SIGNING_V1_ENABLED = "android.injected.signing.v1-enabled";
    String PROPERTY_SIGNING_V2_ENABLED = "android.injected.signing.v2-enabled";

    String PROPERTY_DEPLOY_AS_INSTANT_APP = "android.injected.deploy.instant-app";

    String PROPERTY_SIGNING_COLDSWAP_MODE = "android.injected.coldswap.mode";

    String PROPERTY_APK_SELECT_CONFIG = "android.inject.apkselect.config";

    String PROPERTY_EXTRACT_INSTANT_APK = "android.inject.bundle.extractinstant";

    /** Version code to be used in the built APK. */
    String PROPERTY_VERSION_CODE = "android.injected.version.code";

    /** Version name to be used in the built APK. */
    String PROPERTY_VERSION_NAME = "android.injected.version.name";

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

    String ARTIFACT_MAIN = "_main_";
    String ARTIFACT_ANDROID_TEST = "_android_test_";
    String ARTIFACT_UNIT_TEST = "_unit_test_";

    String FD_INTERMEDIATES = "intermediates";
    String FD_LOGS = "logs";
    String FD_OUTPUTS = "outputs";
    String FD_GENERATED = "generated";

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
