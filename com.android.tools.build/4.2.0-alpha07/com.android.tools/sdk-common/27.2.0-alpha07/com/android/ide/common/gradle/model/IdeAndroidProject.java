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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.ViewBindingOptions;
import com.android.ide.common.repository.GradleVersion;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface IdeAndroidProject extends Serializable {

    int PROJECT_TYPE_APP = 0;
    int PROJECT_TYPE_LIBRARY = 1;
    int PROJECT_TYPE_TEST = 2;
    @Deprecated int PROJECT_TYPE_ATOM = 3;
    int PROJECT_TYPE_INSTANTAPP = 4; // Instant App Bundle
    int PROJECT_TYPE_FEATURE = 5; // com.android.feature module
    int PROJECT_TYPE_DYNAMIC_FEATURE = 6; // com.android.dynamic-feature module

    String ARTIFACT_MAIN = AndroidProject.ARTIFACT_MAIN;
    String ARTIFACT_ANDROID_TEST = AndroidProject.ARTIFACT_ANDROID_TEST;
    String ARTIFACT_UNIT_TEST = AndroidProject.ARTIFACT_UNIT_TEST;
    String FD_GENERATED = AndroidProject.FD_GENERATED;
    String FD_INTERMEDIATES = AndroidProject.FD_INTERMEDIATES;

    int MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD =
            AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD;

    String PROPERTY_ANDROID_SUPPORT_VERSION = AndroidProject.PROPERTY_ANDROID_SUPPORT_VERSION;
    String PROPERTY_APK_LOCATION = AndroidProject.PROPERTY_APK_LOCATION;
    String PROPERTY_APK_SELECT_CONFIG = AndroidProject.PROPERTY_APK_SELECT_CONFIG;
    String PROPERTY_ATTRIBUTION_FILE_LOCATION = AndroidProject.PROPERTY_ATTRIBUTION_FILE_LOCATION;
    String PROPERTY_BUILD_ABI = AndroidProject.PROPERTY_BUILD_ABI;
    String PROPERTY_BUILD_API = AndroidProject.PROPERTY_BUILD_API;
    String PROPERTY_BUILD_API_CODENAME = AndroidProject.PROPERTY_BUILD_API_CODENAME;
    String PROPERTY_BUILD_DENSITY = AndroidProject.PROPERTY_BUILD_DENSITY;
    String PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD =
            AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD;
    String PROPERTY_BUILD_MODEL_ONLY = AndroidProject.PROPERTY_BUILD_MODEL_ONLY;
    String PROPERTY_BUILD_MODEL_ONLY_ADVANCED = AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED;
    String PROPERTY_BUILD_MODEL_ONLY_VERSIONED = AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED;
    String PROPERTY_BUILD_WITH_STABLE_IDS = AndroidProject.PROPERTY_BUILD_WITH_STABLE_IDS;
    String PROPERTY_DEPLOY_AS_INSTANT_APP = AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP;
    String PROPERTY_EXTRACT_INSTANT_APK = AndroidProject.PROPERTY_EXTRACT_INSTANT_APK;
    String PROPERTY_GENERATE_SOURCES_ONLY = AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY;
    String PROPERTY_INJECTED_DYNAMIC_MODULES_LIST =
            AndroidProject.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST;
    String PROPERTY_INVOKED_FROM_IDE = AndroidProject.PROPERTY_INVOKED_FROM_IDE;
    String PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL =
            AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL;
    String PROPERTY_SIGNING_KEY_ALIAS = AndroidProject.PROPERTY_SIGNING_KEY_ALIAS;
    String PROPERTY_SIGNING_KEY_PASSWORD = AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD;
    String PROPERTY_SIGNING_STORE_FILE = AndroidProject.PROPERTY_SIGNING_STORE_FILE;
    String PROPERTY_SIGNING_STORE_PASSWORD = AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD;
    String PROPERTY_SIGNING_V1_ENABLED = AndroidProject.PROPERTY_SIGNING_V1_ENABLED;
    String PROPERTY_SIGNING_V2_ENABLED = AndroidProject.PROPERTY_SIGNING_V2_ENABLED;

    /**
     * Returns the model version. This is a string in the format X.Y.Z
     *
     * @return a string containing the model version.
     */
    @NonNull
    String getModelVersion();

    /**
     * Returns the model api version.
     *
     * <p>This is different from {@link #getModelVersion()} in a way that new model version might
     * increment model version but keep existing api. That means that code which was built against
     * particular 'api version' might be safely re-used for all new model versions as long as they
     * don't change the api.
     *
     * <p>Every new model version is assumed to return an 'api version' value which is equal or
     * greater than the value used by the previous model version.
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
     * <p>This does not include test variant. Test variants are additional artifacts in their
     * respective variant info.
     *
     * @return a list of the variants.
     */
    @NonNull
    Collection<IdeVariant> getVariants();

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

    /** Returns a list of {@link SigningConfig}. */
    @NonNull
    Collection<SigningConfig> getSigningConfigs();

    /** Returns the aapt options. */
    @NonNull
    AaptOptions getAaptOptions();

    /** Returns the lint options. */
    @NonNull
    IdeLintOptions getLintOptions();

    /**
     * Returns the dependencies that were not successfully resolved. The returned list gets
     * populated only if the system property {@link
     * com.android.builder.model.AndroidProject#PROPERTY_BUILD_MODEL_ONLY} has been set to {@code
     * true}.
     *
     * <p>Each value of the collection has the format group:name:version, for example:
     * com.google.guava:guava:15.0.2
     *
     * @return the dependencies that were not successfully resolved.
     * @deprecated use {@link #getSyncIssues()}
     */
    @Deprecated
    @NonNull
    Collection<String> getUnresolvedDependencies();

    /**
     * Returns issues found during sync. The returned list gets populated only if the system
     * property {@link com.android.builder.model.AndroidProject#PROPERTY_BUILD_MODEL_ONLY} has been
     * set to {@code true}.
     */
    @NonNull
    Collection<SyncIssue> getSyncIssues();

    /** Returns the compile options for Java code. */
    @NonNull
    JavaCompileOptions getJavaCompileOptions();

    /** Returns the build folder of this project. */
    @NonNull
    File getBuildFolder();

    /**
     * Returns the resource prefix to use, if any. This is an optional prefix which can be set and
     * which is used by the defaults to automatically choose new resources with a certain prefix,
     * warn if resources are not using the given prefix, etc. This helps work with resources in the
     * app namespace where there could otherwise be unintentional duplicated resource names between
     * unrelated libraries.
     *
     * @return the optional resource prefix, or null if not set
     */
    @Nullable
    String getResourcePrefix();

    /**
     * Returns the build tools version used by this module.
     *
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

    @Nullable
    ViewBindingOptions getViewBindingOptions();

    @Nullable
    IdeDependenciesInfo getDependenciesInfo();

    @Nullable
    GradleVersion getParsedModelVersion();

    /**
     * Returns the optional group-id of the artifact represented by this project.
     *
     * @since 3.6
     */
    @Nullable
    String getGroupId();

    /** Various flags from AGP */
    @NonNull
    IdeAndroidGradlePluginProjectFlags getAgpFlags();

    void forEachVariant(@NonNull Consumer<IdeVariant> action);

    /**
     * Returns the minimal information of variants for this project, excluding test related
     * variants.
     *
     * @since 4.1
     */
    @NonNull
    Collection<IdeVariantBuildInformation> getVariantsBuildInformation();

    /**
     * Returns the lint jars that this module uses to run extra lint checks.
     *
     * <p>If null, the model does not contain the information because AGP was an older version, and
     * alternative ways to get the information should be used.
     */
    @Nullable
    List<File> getLintRuleJars();

    /**
     * Temporary storage of named data associated with this project. Intended for purposes such as
     * caching data associated with a project. A null value deletes the associated entry. Note that
     * the data is transient and will not be kept across sessions.
     */
    @Nullable
    Object putClientProperty(@NonNull String key, @Nullable Object value);

    /**
     * Retrieves named data that was previously stored via {@link #putClientProperty(String,
     * Object)}.
     */
    @Nullable
    Object getClientProperty(@NonNull String key);
}
