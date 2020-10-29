/*
 * Copyright (C) 2014 The Android Open Source Project
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
import java.util.List;

/**
 * Class representing a sync issue. The goal is to make these issues not fail the sync but instead
 * report them at the end of a successful sync.
 */
public interface SyncIssue {

    int SEVERITY_WARNING = 1;
    int SEVERITY_ERROR = 2;

    /** Generic error with no data payload, and no expected quick fix in IDE. */
    int TYPE_GENERIC = 0;

    /** Data is expiration data. */
    int TYPE_PLUGIN_OBSOLETE = 1;

    /** Data is dependency coordinate. */
    int TYPE_UNRESOLVED_DEPENDENCY = 2;

    /** Data is dependency coordinate. */
    int TYPE_DEPENDENCY_IS_APK = 3;

    /** Data is dependency coordinate. */
    int TYPE_DEPENDENCY_IS_APKLIB = 4;

    /** Data is local file. */
    int TYPE_NON_JAR_LOCAL_DEP = 5;

    /** Data is dependency coordinate/path. */
    int TYPE_NON_JAR_PACKAGE_DEP = 6;

    /** Data is dependency coordinate/path. */
    int TYPE_NON_JAR_PROVIDED_DEP = 7;

    /** Data is dependency coordinate/path. */
    int TYPE_JAR_DEPEND_ON_AAR = 8;

    /**
     * Mismatch dependency version between tested and test app. Data is dep coordinate without the
     * version (groupId:artifactId)
     */
    int TYPE_MISMATCH_DEP = 9;

    /** Data is dependency coordinate. */
    int TYPE_OPTIONAL_LIB_NOT_FOUND = 10;

    /** Data is variant name. */
    int TYPE_JACK_IS_NOT_SUPPORTED = 11;

    /** Data is the min version of Gradle. */
    int TYPE_GRADLE_TOO_OLD = 12;

    /** Data is the required min build tools version, parsable by Revision. */
    int TYPE_BUILD_TOOLS_TOO_LOW = 13;

    /**
     * Found dependency that's the maven published android.jar. Data is the maven artifact
     * coordinates.
     */
    int TYPE_DEPENDENCY_MAVEN_ANDROID = 14;

    /**
     * Found dependency that is known to be inside android.jar. Data is maven artifact coordinates.
     */
    int TYPE_DEPENDENCY_INTERNAL_CONFLICT = 15;

    /** Errors configuring NativeConfigValues for individual individual variants */
    int TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION = 16;

    /**
     * Errors configuring NativeConfigValues. There was a process exception. Data contains STDERR
     * which should be interpreted by Android Studio.
     */
    int TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION = 17;

    /** Cannot use Java 8 Language features without Jack. */
    int TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES = 18;

    /**
     * A wearApp configuration was resolved and found more than one apk. Data is the configuration
     * name.
     */
    int TYPE_DEPENDENCY_WEAR_APK_TOO_MANY = 19;

    /** A wearApp configuration was resolved and found an apk even though unbundled mode is on. */
    int TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED = 20;

    /** Data is dependency coordinate/path. */
    @Deprecated int TYPE_JAR_DEPEND_ON_ATOM = 21;

    /** Data is dependency coordinate/path. */
    @Deprecated int TYPE_AAR_DEPEND_ON_ATOM = 22;

    /** Data is dependency coordinate/path. */
    @Deprecated int TYPE_ATOM_DEPENDENCY_PROVIDED = 23;

    /**
     * Indicates that a required SDK package was not installed. The data field contains the sdklib
     * package ID of the missing package that the user should install.
     */
    int TYPE_MISSING_SDK_PACKAGE = 24;

    /**
     * Indicates that the plugin requires a newer version of studio. Minimum version is passed in
     * the data.
     */
    int TYPE_STUDIO_TOO_OLD = 25;

    /**
     * Indicates that the module contains flavors but that no dimensions have been named. data is
     * empty.
     */
    int TYPE_UNNAMED_FLAVOR_DIMENSION = 26;

    /** An incompatible plugin is used. */
    int TYPE_INCOMPATIBLE_PLUGIN = 27;

    /**
     * Indicates that the project uses a deprecated DSL. The data paylod is dslElement::removeTarget
     * where removal target is the version of the plugin where the dsl element is targeted to be
     * removed.
     */
    int TYPE_DEPRECATED_DSL = 28;

    /**
     * Indicates that the project uses a deprecated configuration.
     *
     * <p>This type is now replaced with TYPE_USING_DEPRECATED_CONFIGURATION (see
     * http://issuetracker.google.com/138278313).
     */
    @SuppressWarnings("unused")
    @Deprecated
    int TYPE_DEPRECATED_CONFIGURATION = 29;

    /**
     * Indicates that the project uses a deprecated DSL, the Data payload is a URL giving context to
     * the user on how to remove the deprecated element or value.
     *
     * <p>This type is now replaced with TYPE_USING_DEPRECATED_DSL_VALUE (see
     * http://issuetracker.google.com/138278313).
     */
    @SuppressWarnings("unused")
    @Deprecated
    int TYPE_DEPRECATED_DSL_VALUE = 29;

    /** Indicates that the project contains the min sdk in the android manifest file. */
    int TYPE_MIN_SDK_VERSION_IN_MANIFEST = 30;

    /** Indicates that the project contains the target sdk in the android manifest file. */
    int TYPE_TARGET_SDK_VERSION_IN_MANIFEST = 31;

    /** Indicated that an experimental gradle project option is used. */
    int TYPE_UNSUPPORTED_PROJECT_OPTION_USE = 32;

    /**
     * Indicates that building the configuration rules for this project requires parsing the
     * manifest file.
     */
    int TYPE_MANIFEST_PARSED_DURING_CONFIGURATION = 33;

    /**
     * Indicates that the version of a third-party Gradle plugin (not the Android Gradle plugin) is
     * not supported and needs to be updated.
     */
    int TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD = 34;

    /**
     * Indicates that the signing configuration is declared in the dynamic-feature gradle file. This
     * should only be declared in the application module, as dynamic-features use the base module's
     * signing configuration, and this will be ignored.
     */
    int TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE = 35;

    /**
     * Indicates that the SDK is missing or invalid, this can either be set in the ANDROID_SDK_ROOT
     * environment variable or the projects local.properties files.
     */
    int TYPE_SDK_NOT_SET = 36;

    /** Indicates that the user has specified multiple default build types */
    int TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT = 37;

    /** Indicates that the user has specified multiple default product flavors */
    int TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT = 38;

    /** Indicates that the compileSdkVersion is missing */
    int TYPE_COMPILE_SDK_VERSION_NOT_SET = 39;

    /**
     * Indicates that the `android.useAndroidX` property must be enabled but is currently disabled.
     */
    int TYPE_ANDROID_X_PROPERTY_NOT_ENABLED = 40;

    /** Indicates that the project uses a deprecated configuration. */
    int TYPE_USING_DEPRECATED_CONFIGURATION = 41;

    /**
     * Indicates that the project uses a deprecated DSL, the Data payload is a URL giving context to
     * the user on how to remove the deprecated element or value.
     */
    int TYPE_USING_DEPRECATED_DSL_VALUE = 42;

    /** The user or a plugin has tried to mutate a DSL value after it has been locked. */
    int TYPE_EDIT_LOCKED_DSL_VALUE = 43;

    // NOTE: When adding a new type here, increment the index by 1. This index may not be consistent
    // with the corresponding value in studio_stats.proto (e.g., it could be lower by 1), because of
    // an indexing issue in the past (see http://issuetracker.google.com/138278313).

    /** Returns the severity of the issue. */
    int getSeverity();

    /** Returns the type of the issue. */
    int getType();

    /**
     * Returns the data of the issue.
     *
     * <p>This is a machine-readable string used by the IDE for known issue types.
     */
    @Nullable
    String getData();

    /**
     * Returns the a user-readable message for the issue.
     *
     * <p>This is used by IDEs that do not recognize the issue type (ie older IDE released before
     * the type was added to the plugin).
     */
    @NonNull
    String getMessage();

    /**
     * Returns the a user-readable nulti-line message for the issue.
     *
     * <p>This is an optional extension of {@link #getMessage()}
     */
    @Nullable
    List<String> getMultiLineMessage();
}
