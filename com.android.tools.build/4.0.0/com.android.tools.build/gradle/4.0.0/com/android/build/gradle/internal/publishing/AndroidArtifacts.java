/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.publishing;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.REVERSE_METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;

import com.android.annotations.NonNull;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;

/**
 * Helper for publishing android artifacts, both for internal (inter-project) and external
 * (to repositories).
 */
public class AndroidArtifacts {
    public static final Attribute<String> ARTIFACT_TYPE = Attribute.of("artifactType", String.class);
    public static final Attribute<String> MODULE_PATH = Attribute.of("modulePath", String.class);

    // types for main artifacts
    private static final String TYPE_AAR = "aar";
    private static final String TYPE_PROCESSED_AAR = "processed-aar";
    private static final String TYPE_APK = "apk";
    private static final String TYPE_JAR = ArtifactTypeDefinition.JAR_TYPE;
    private static final String TYPE_BUNDLE = "aab";
    // The apks produced from the android app bundle
    private static final String TYPE_APKS_FROM_BUNDLE = "bundle-apks";
    // zip of apks for publishing single or multi-apks to a repo.
    private static final String TYPE_APK_ZIP = "zip";

    // type for processed jars (the jars may need to be processed, e.g. jetified to AndroidX, before
    // they can be used)
    private static final String TYPE_PROCESSED_JAR = "processed-jar";

    private static final String TYPE_CLASSES = "android-classes";

    // types published by an Android library
    private static final String TYPE_CLASSES_JAR = "android-classes-jar"; // In AAR
    private static final String TYPE_CLASSES_DIR = "android-classes-directory"; // Not in AAR
    private static final String TYPE_NON_NAMESPACED_CLASSES = "non-namespaced-android-classes";
    private static final String TYPE_SHARED_CLASSES = "android-shared-classes";
    private static final String TYPE_DEX = "android-dex";
    private static final String TYPE_DEX_AND_KEEP_RULES = "android-dex-and-keep-rules";
    private static final String TYPE_KEEP_RULES = "android-keep-rules";
    private static final String TYPE_JAVA_RES = "android-java-res";
    private static final String TYPE_SHARED_JAVA_RES = "android-shared-java-res";
    private static final String TYPE_MANIFEST = "android-manifest";
    private static final String TYPE_NON_NAMESPACED_MANIFEST = "non-namespaced-android-manifest";
    private static final String TYPE_MANIFEST_METADATA = "android-manifest-metadata";
    private static final String TYPE_ANDROID_RES = "android-res";
    private static final String TYPE_ANDROID_NAMESPACED_R_CLASS_JAR =
            "android-res-namespaced-r-class-jar";
    private static final String TYPE_ANDROID_RES_STATIC_LIBRARY = "android-res-static-library";
    private static final String TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY =
            "android-res-shared-static-library";
    private static final String TYPE_ANDROID_RES_BUNDLE = "android-res-for-bundle";
    private static final String TYPE_ASSETS = "android-assets";
    private static final String TYPE_SHARED_ASSETS = "android-shared-assets";
    private static final String TYPE_JNI = "android-jni";
    private static final String TYPE_SHARED_JNI = "android-shared-jni";
    private static final String TYPE_AIDL = "android-aidl";
    private static final String TYPE_RENDERSCRIPT = "android-renderscript";
    private static final String TYPE_LINT_JAR = "android-lint";
    private static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    private static final String TYPE_PUBLIC_RES = "android-public-res";
    private static final String TYPE_SYMBOL = "android-symbol";
    private static final String TYPE_SYMBOL_WITH_PACKAGE_NAME = "android-symbol-with-package-name";
    private static final String TYPE_DEFINED_ONLY_SYMBOL = "defined-only-android-symbol";
    private static final String TYPE_UNFILTERED_PROGUARD_RULES = "android-consumer-proguard-rules";
    private static final String TYPE_FILTERED_PROGUARD_RULES = "android-filtered-proguard-rules";
    private static final String TYPE_AAPT_PROGUARD_RULES = "android-aapt-proguard-rules";
    private static final String TYPE_DATA_BINDING_ARTIFACT = "android-databinding";
    private static final String TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT =
            "android-databinding-class-log";
    private static final String TYPE_EXPLODED_AAR = "android-exploded-aar";
    private static final String TYPE_AAR_OR_JAR = "android-aar-or-jar";
    private static final String TYPE_COMPILED_DEPENDENCIES_RESOURCES =
            "android-compiled-dependencies-resources";
    private static final String TYPE_MODULE_BUNDLE = "android-module-bundle";
    private static final String TYPE_LIB_DEPENDENCIES = "android-lib-dependencies";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    // types for APK to APK support (base/feature/tests)
    private static final String TYPE_PACKAGED_DEPENDENCIES = "android-packaged-dependencies";

    // types for feature-split content.
    private static final String TYPE_FEATURE_SET_METADATA = "android-feature-all-metadata";
    private static final String TYPE_BASE_MODULE_METADATA = "android-base-module-metadata";
    private static final String TYPE_FEATURE_RESOURCE_PKG = "android-feature-res-ap_";
    private static final String TYPE_FEATURE_DEX = "android-feature-dex";
    private static final String TYPE_FEATURE_SIGNING_CONFIG = "android-feature-signing-config";
    private static final String TYPE_FEATURE_NAME = "android-feature-name";


    // types for metadata content.
    private static final String TYPE_REVERSE_METADATA_FEATURE_DECLARATION =
            "android-reverse-metadata-feature-decl";
    private static final String TYPE_REVERSE_METADATA_FEATURE_MANIFEST =
            "android-reverse-metadata-feature-manifest";
    private static final String TYPE_REVERSE_METADATA_CLASSES = "android-reverse-metadata-classes";
    private static final String TYPE_REVERSE_METADATA_JAVA_RES =
            "android-reverse-metadata-java-res";

    public static final String TYPE_MOCKABLE_JAR = "android-mockable-jar";
    public static final Attribute<Boolean> MOCKABLE_JAR_RETURN_DEFAULT_VALUES =
            Attribute.of("returnDefaultValues", Boolean.class);
    // attr info extracted from the platform android.jar
    public static final String TYPE_PLATFORM_ATTR = "android-platform-attr";

    private static final String TYPE_NAVIGATION_JSON = "android-navigation-json";

    private static final String TYPE_PREFAB_PACKAGE = "android-prefab";

    private static final String TYPE_DESUGAR_LIB_PROJECT_KEEP_RULES =
            "android-desugar-lib-project-keep-rules";
    private static final String TYPE_DESUGAR_LIB_SUBPROJECT_KEEP_RULES =
            "android-desugar-lib-subproject-keep-rules";
    private static final String TYPE_DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES =
            "android-desugar-lib-external-libs-keep-rules";
    private static final String TYPE_DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES =
            "android-desugar-lib-mixed-scope-keep-rules";
    private static final String TYPE_DESUGAR_LIB_EXTERNAL_FILE_KEEP_RULES =
            "android-desugar-lib-external-file-keep-rules";

    public enum ConsumedConfigType {
        COMPILE_CLASSPATH("compileClasspath", API_ELEMENTS, true),
        RUNTIME_CLASSPATH("runtimeClasspath", RUNTIME_ELEMENTS, true),
        ANNOTATION_PROCESSOR("annotationProcessorClasspath", RUNTIME_ELEMENTS, false),
        REVERSE_METADATA_VALUES("reverseMetadata", REVERSE_METADATA_ELEMENTS, false);

        @NonNull private final String name;
        @NonNull private final PublishedConfigType publishedTo;
        private final boolean needsTestedComponents;

        ConsumedConfigType(
                @NonNull String name,
                @NonNull PublishedConfigType publishedTo,
                boolean needsTestedComponents) {
            this.name = name;
            this.publishedTo = publishedTo;
            this.needsTestedComponents = needsTestedComponents;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public PublishedConfigType getPublishedTo() {
            return publishedTo;
        }

        public boolean needsTestedComponents() {
            return needsTestedComponents;
        }
    }

    public enum PublishedConfigType {
        API_ELEMENTS, // inter-project publishing (API)
        RUNTIME_ELEMENTS, // inter-project publishing (RUNTIME)
        REVERSE_METADATA_ELEMENTS, // inter-project publishing (REVERSE META-DATA)

        // Maven/SoftwareComponent AAR publishing (API, w/o variant-specific attributes)
        API_PUBLICATION(true, false),
        // Maven/SoftwareComponent AAR publishing (RUNTIME, w/o variant-specific attributes)
        RUNTIME_PUBLICATION(true, false),
        // Maven/SoftwareComponent AAR publishing (API, with variant-specific attributes)
        ALL_API_PUBLICATION(true, true),
        // Maven/SoftwareComponent AAR publishing (RUNTIME, with variant-specific attributes)
        ALL_RUNTIME_PUBLICATION(true, true),

        APK_PUBLICATION(true, false), // Maven/SoftwareComponent APK publishing
        AAB_PUBLICATION(true, false); // Maven/SoftwareComponent AAB publishing

        private boolean isPublicationConfig;
        private boolean isClassifierRequired;

        PublishedConfigType(boolean isPublicationConfig, boolean isClassifierRequired) {
            this.isPublicationConfig = isPublicationConfig;
            this.isClassifierRequired = isClassifierRequired;
        }

        PublishedConfigType() {
            this(false, false);
        }

        public boolean isPublicationConfig() {
            return isPublicationConfig;
        }

        /**
         * Some publishing configurations require setting the classifier. This is because artifacts
         * from those configurations are added to a single software component, and unless there is a
         * classifier, POM cannot choose the main artifact.
         *
         * <p>E.g. when publishing an AAR that has debug and release variants, there will be two AAR
         * to publish. POM publishing ignores configuration attributes, and it has to use
         * classifiers in order to de-duplicate artifacts. In this case, to disambiguate between
         * these two artifacts, they need to have different classifiers specified when publishing
         * them.
         */
        public boolean isClassifierRequired() {
            return isClassifierRequired;
        }
    }

    /** The provenance of artifacts to include. */
    public enum ArtifactScope {
        /** Include all artifacts */
        ALL,
        /** Include all 'external' artifacts, i.e. everything but PROJECT, i.e. FILE + MODULE */
        EXTERNAL,
        /** Include all artifacts built by subprojects */
        PROJECT,
        /** Include all file dependencies */
        FILE,
        /** Include all module (i.e. from a repository) dependencies */
        REPOSITORY_MODULE,
    }

    /** Artifact published by modules for consumption by other modules. */
    public enum ArtifactType {

        /**
         * A jar or directory containing classes.
         *
         * <p>If it is a directory, it must contain class files only and not jars.
         */
        CLASSES(TYPE_CLASSES),

        /** A jar containing classes. */
        CLASSES_JAR(TYPE_CLASSES_JAR),

        /**
         * A directory containing classes.
         *
         * <p>IMPORTANT: The directory may contain either class files only (preferred) or a single
         * jar only, see {@link ClassesDirFormat}. Because of this, DO NOT CONSUME this artifact
         * type directly, use {@link #CLASSES} or {@link #CLASSES_JAR} instead. (We have {@link
         * com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform} from {@link
         * #CLASSES_DIR} to {@link #CLASSES} to normalize the format.)
         */
        CLASSES_DIR(TYPE_CLASSES_DIR),

        // classes.jar files from libraries that are not namespaced yet, and need to be rewritten to
        // be namespace aware.
        NON_NAMESPACED_CLASSES(TYPE_NON_NAMESPACED_CLASSES),
        SHARED_CLASSES(TYPE_SHARED_CLASSES),
        // Jar or processed jar, used for purposes such as computing the annotation processor
        // classpath or building the model.
        // IMPORTANT: Consumers should generally use PROCESSED_JAR instead of JAR, as the jars may
        // need to be processed (e.g., jetified to AndroidX) before they can be used. Consuming JAR
        // should be considered as an exception and the reason should be documented.
        JAR(TYPE_JAR),
        PROCESSED_JAR(TYPE_PROCESSED_JAR),
        // published dex folder for bundle
        DEX(TYPE_DEX),
        // dex and keep rules(shrinking desugar lib), a folder with a subfolder named dex
        // which contains dex files, and with a file named keep_rules
        DEX_AND_KEEP_RULES(TYPE_DEX_AND_KEEP_RULES),
        // a file named keep_rules for shrinking desugar lib
        KEEP_RULES(TYPE_KEEP_RULES),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST),
        // manifests that need to be auto-namespaced.
        NON_NAMESPACED_MANIFEST(TYPE_NON_NAMESPACED_MANIFEST),
        MANIFEST_METADATA(TYPE_MANIFEST_METADATA),

        // Resources static library are API (where only explicit dependencies are included) and
        // runtime
        RES_STATIC_LIBRARY(TYPE_ANDROID_RES_STATIC_LIBRARY),
        RES_SHARED_STATIC_LIBRARY(TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY),
        RES_BUNDLE(TYPE_ANDROID_RES_BUNDLE),

        // API only elements.
        AIDL(TYPE_AIDL),
        RENDERSCRIPT(TYPE_RENDERSCRIPT),
        DATA_BINDING_ARTIFACT(TYPE_DATA_BINDING_ARTIFACT),
        DATA_BINDING_BASE_CLASS_LOG_ARTIFACT(TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
        COMPILE_ONLY_NAMESPACED_R_CLASS_JAR(TYPE_ANDROID_NAMESPACED_R_CLASS_JAR),

        // runtime and/or bundle elements
        JAVA_RES(TYPE_JAVA_RES),
        SHARED_JAVA_RES(TYPE_SHARED_JAVA_RES),
        ANDROID_RES(TYPE_ANDROID_RES),
        ASSETS(TYPE_ASSETS),
        SHARED_ASSETS(TYPE_SHARED_ASSETS),
        COMPILE_SYMBOL_LIST(TYPE_SYMBOL),
        COMPILED_DEPENDENCIES_RESOURCES(TYPE_COMPILED_DEPENDENCIES_RESOURCES),
        /**
         * The symbol list with the package name as the first line. As the r.txt format in the AAR
         * cannot be changed, this is created by prepending the package name from the
         * AndroidManifest.xml to the existing r.txt file.
         */
        SYMBOL_LIST_WITH_PACKAGE_NAME(TYPE_SYMBOL_WITH_PACKAGE_NAME),
        DEFINED_ONLY_SYMBOL_LIST(TYPE_DEFINED_ONLY_SYMBOL),
        JNI(TYPE_JNI),
        SHARED_JNI(TYPE_SHARED_JNI),

        /**
         * A directory containing a Prefab package.json file and associated modules.
         *
         * <p>Processed by Prefab to generate inputs for ExternalNativeBuild modules to consume
         * dependencies from AARs. https://google.github.io/prefab/
         */
        PREFAB_PACKAGE(TYPE_PREFAB_PACKAGE),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS),
        PUBLIC_RES(TYPE_PUBLIC_RES),
        UNFILTERED_PROGUARD_RULES(TYPE_UNFILTERED_PROGUARD_RULES),
        FILTERED_PROGUARD_RULES(TYPE_FILTERED_PROGUARD_RULES),
        AAPT_PROGUARD_RULES(TYPE_AAPT_PROGUARD_RULES),

        LINT(TYPE_LINT_JAR),

        APK_MAPPING(TYPE_MAPPING),
        APK_METADATA(TYPE_METADATA),
        APK(TYPE_APK),
        // zip of apks for publishing single or multi-apks to a repo.
        APK_ZIP(TYPE_APK_ZIP),

        // intermediate bundle that only contains one module. This is to be input into bundle-tool
        MODULE_BUNDLE(TYPE_MODULE_BUNDLE),
        // final bundle generate by bundle-tool
        BUNDLE(TYPE_BUNDLE),
        // apks produced from the bundle, for consumption by tests.
        APKS_FROM_BUNDLE(TYPE_APKS_FROM_BUNDLE),
        // intermediate library dependencies on a per module basis for eventual packaging in the
        // bundle.
        LIB_DEPENDENCIES(TYPE_LIB_DEPENDENCIES),

        // Dynamic Feature related artifacts.

        // file containing the metadata for the full feature set. This contains the feature names,
        // the res ID offset, both tied to the feature module path. Published by the base for the
        // other features to consume and find their own metadata.
        FEATURE_SET_METADATA(TYPE_FEATURE_SET_METADATA),
        FEATURE_SIGNING_CONFIG(TYPE_FEATURE_SIGNING_CONFIG),

        // file containing the base module info (appId, versionCode, debuggable, ...).
        // This is published by the base module and read by the dynamic feature modules
        BASE_MODULE_METADATA(TYPE_BASE_MODULE_METADATA),

        // ?
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG),

        // File containing the list of dependencies packaged in a given APK. This is consumed
        // by other APKs to avoid repackaging the same thing.
        PACKAGED_DEPENDENCIES(TYPE_PACKAGED_DEPENDENCIES),

        // The feature dex files output by the DexSplitter from the base. The base produces and
        // publishes these files when there's multi-apk code shrinking.
        FEATURE_DEX(TYPE_FEATURE_DEX),

        // The name of an instant or dynamic feature module
        // This is published by {@link FeatureNameWriterTask} to be consumed by dependencies
        // of the feature that need to know the name of its feature split
        FEATURE_NAME(TYPE_FEATURE_NAME),

        // Reverse Metadata artifacts
        REVERSE_METADATA_FEATURE_DECLARATION(TYPE_REVERSE_METADATA_FEATURE_DECLARATION),
        REVERSE_METADATA_FEATURE_MANIFEST(TYPE_REVERSE_METADATA_FEATURE_MANIFEST),
        REVERSE_METADATA_CLASSES(TYPE_REVERSE_METADATA_CLASSES),
        REVERSE_METADATA_JAVA_RES(TYPE_REVERSE_METADATA_JAVA_RES),

        // types for querying only. Not publishable.
        AAR(TYPE_AAR),
        // Artifact type for processed aars (the aars may need to be processed, e.g. jetified to
        // AndroidX, before they can be used)
        PROCESSED_AAR(TYPE_PROCESSED_AAR),
        EXPLODED_AAR(TYPE_EXPLODED_AAR),
        AAR_OR_JAR(TYPE_AAR_OR_JAR), // See ArtifactUtils for how this is used.

        NAVIGATION_JSON(TYPE_NAVIGATION_JSON),

        DESUGAR_LIB_PROJECT_KEEP_RULES(TYPE_DESUGAR_LIB_PROJECT_KEEP_RULES),
        DESUGAR_LIB_SUBPROJECT_KEEP_RULES(TYPE_DESUGAR_LIB_SUBPROJECT_KEEP_RULES),
        DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES(TYPE_DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES),
        DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES(TYPE_DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES),
        DESUGAR_LIB_EXTERNAL_FILE_KEEP_RULES(TYPE_DESUGAR_LIB_EXTERNAL_FILE_KEEP_RULES);

        @NonNull private final String type;

        ArtifactType(@NonNull String type) {
            this.type = type;
        }

        @NonNull
        public String getType() {
            return type;
        }
    }

    /**
     * The format of the directory with artifact type {@link
     * AndroidArtifacts.ArtifactType#CLASSES_DIR}.
     *
     * <p>See {@link #CONTAINS_SINGLE_JAR} for why we need this format.
     */
    public enum ClassesDirFormat {

        /** The directory contains class files only. */
        CONTAINS_CLASS_FILES_ONLY,

        /**
         * The directory contains a single jar only.
         *
         * <p>The need for this format arises when we want to publish all classes to a directory,
         * but the input classes contain jars (e.g., R.jar or those provided by external
         * users/plugins via the AGP API). There are a few approaches, and only the last one works:
         *
         * <p>1. Unzip the jars into the directory: This operation may fail due to OS differences
         * (e.g., case sensitivity, char encoding). It is also inefficient to zip and unzip classes
         * multiple times.
         *
         * <p>2. Put the jars inside the directory: The directory is usually put on a classpath, and
         * the jars inside the directory would not be recognized as part of the classpath. Here are
         * 2 attempts to fix it: 2a) At the consumer's side, modify the classpath to include the
         * jars inside the directory. This is possible at the task/transform's execution but not
         * possible at task graph creation. Therefore, Gradle would not apply input normalization
         * correctly to the jars inside the directory. 2b) Add a transform to convert the directory
         * into a jar. This transform takes all the class files inside the jars and merge them into
         * a jar. Then, we can let consumers consume the jar instead of the directory. This is
         * possible but not as efficient as #3 below.
         *
         * <p>3. Merge all the class files inside the jars into a single jar inside the directory,
         * then add a transform to convert the directory into a jar where the transform simply
         * selects the jar inside the directory as its output jar (see {@link
         * com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform}). This is
         * better than 2b because 2b includes copying files when publishing and zipping files when
         * transforming, whereas this includes zipping files when publishing and nearly a no-op when
         * transforming.
         */
        CONTAINS_SINGLE_JAR
    }
}
