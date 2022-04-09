/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.publishing

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.SingleArtifact.APK
import com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST
import com.android.build.api.artifact.SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT
import com.android.build.api.artifact.SingleArtifact.OBFUSCATION_MAPPING_FILE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_PUBLICATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.REVERSE_METADATA_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.AAPT_PROGUARD_FILE
import com.android.build.gradle.internal.scope.InternalArtifactType.AAR_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.AIDL_PARCELABLE
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_ZIP
import com.android.build.gradle.internal.scope.InternalArtifactType.APP_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.BASE_MODULE_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILED_LOCAL_RESOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.CONSUMER_PROGUARD_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_MERGED_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_PUBLISHED_DEX
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_NAME
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_SET_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.FULL_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JNI
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_MODEL_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PUBLISH_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LOCAL_AAR_FOR_LINT
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_DEBUG_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_SYMBOL_TABLES
import com.android.build.gradle.internal.scope.InternalArtifactType.NAVIGATION_JSON
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_DEPENDENCIES
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PREFAB_PACKAGE
import com.android.build.gradle.internal.scope.InternalArtifactType.PREFAB_PACKAGE_CONFIGURATION
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLIC_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_HEADERS
import com.android.build.gradle.internal.scope.InternalArtifactType.RES_STATIC_LIBRARY
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.SIGNING_CONFIG_DATA
import com.android.build.gradle.internal.scope.InternalArtifactType.SIGNING_CONFIG_VERSIONS
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.FileSystemLocation

/**
 * Publishing spec for variants and tasks outputs.
 *
 *
 * This builds a bi-directional mapping between task outputs and published artifacts (for project
 * to project publication), as well as where to publish the artifact (with
 * [org.gradle.api.artifacts.Configuration] via the [PublishedConfigType] enum.)
 *
 *
 * This mapping is per [VariantType] to allow for different task outputs to be published
 * under the same [ArtifactType].
 */
class PublishingSpecs {

    /**
     * The publishing spec for a variant
     */
    interface VariantSpec {
        val variantType: VariantType
        val outputs: Set<OutputSpec>

        fun getSpec(artifactType: ArtifactType, publishConfigType: PublishedConfigType?): OutputSpec?
    }

    /**
     * A published output
     */
    interface OutputSpec {
        val outputType: Artifact.Single<out FileSystemLocation>
        val artifactType: ArtifactType
        val publishedConfigTypes: ImmutableList<PublishedConfigType>
        val libraryElements: String?
    }

    companion object {
        private var builder: ImmutableMap.Builder<VariantType, VariantSpec>? = ImmutableMap.builder()
        private lateinit var variantMap: Map<VariantType, VariantSpec>

        init {
            variantSpec(VariantTypeImpl.BASE_APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                output(APP_CLASSES, ArtifactType.CLASSES_JAR)
                output(JAVA_RES, ArtifactType.JAVA_RES)
                api(OBFUSCATION_MAPPING_FILE, ArtifactType.APK_MAPPING)

                api(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)

                // FIXME: need data binding artifacts as well for Dynamic apps.

                runtime(APK, ArtifactType.APK)
                publish(APK_ZIP, ArtifactType.APK_ZIP)

                runtime(InternalArtifactType.APKS_FROM_BUNDLE, ArtifactType.APKS_FROM_BUNDLE)
                runtime(PACKAGED_DEPENDENCIES, ArtifactType.PACKAGED_DEPENDENCIES)

                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)

                // output of bundle-tool
                publish(com.android.build.api.artifact.SingleArtifact.BUNDLE, ArtifactType.BUNDLE)

                // this is only for base modules.
                api(FEATURE_SET_METADATA, ArtifactType.FEATURE_SET_METADATA)
                api(BASE_MODULE_METADATA, ArtifactType.BASE_MODULE_METADATA)
                api(SIGNING_CONFIG_DATA, ArtifactType.FEATURE_SIGNING_CONFIG_DATA)
                api(SIGNING_CONFIG_VERSIONS, ArtifactType.FEATURE_SIGNING_CONFIG_VERSIONS)
                runtime(LINT_MODEL, ArtifactType.LINT_MODEL)
                // publish the LINT_MODEL again as BASE_MODULE_LINT_MODEL for consumption by dynamic
                // features when writing their lint models to be published back to the app.
                runtime(LINT_MODEL, ArtifactType.BASE_MODULE_LINT_MODEL)
            }

            variantSpec(VariantTypeImpl.OPTIONAL_APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                output(APP_CLASSES, ArtifactType.CLASSES_JAR)
                output(JAVA_RES, ArtifactType.JAVA_RES)
                api(OBFUSCATION_MAPPING_FILE, ArtifactType.APK_MAPPING)

                api(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)

                // FIXME: need data binding artifacts as well for Dynamic apps.

                runtime(APK, ArtifactType.APK)
                runtime(PACKAGED_DEPENDENCIES, ArtifactType.PACKAGED_DEPENDENCIES)

                // The intermediate bundle containing only this module. Input for bundle-tool
                reverseMetadata(MODULE_BUNDLE, ArtifactType.MODULE_BUNDLE)
                reverseMetadata(METADATA_LIBRARY_DEPENDENCIES_REPORT, ArtifactType.LIB_DEPENDENCIES)

                // this is only for non-base modules.
                reverseMetadata(METADATA_FEATURE_DECLARATION, ArtifactType.REVERSE_METADATA_FEATURE_DECLARATION)
                reverseMetadata(METADATA_FEATURE_MANIFEST, ArtifactType.REVERSE_METADATA_FEATURE_MANIFEST)
                reverseMetadata(MODULE_AND_RUNTIME_DEPS_CLASSES, ArtifactType.REVERSE_METADATA_CLASSES)
                reverseMetadata(MERGED_JAVA_RES, ArtifactType.REVERSE_METADATA_JAVA_RES)
                reverseMetadata(CONSUMER_PROGUARD_DIR, ArtifactType.UNFILTERED_PROGUARD_RULES)
                reverseMetadata(AAPT_PROGUARD_FILE, ArtifactType.AAPT_PROGUARD_RULES)
                reverseMetadata(PACKAGED_DEPENDENCIES, ArtifactType.PACKAGED_DEPENDENCIES)
                reverseMetadata(NATIVE_DEBUG_METADATA, ArtifactType.REVERSE_METADATA_NATIVE_DEBUG_METADATA)
                reverseMetadata(NATIVE_SYMBOL_TABLES, ArtifactType.REVERSE_METADATA_NATIVE_SYMBOL_TABLES)
                reverseMetadata(DESUGAR_LIB_MERGED_KEEP_RULES, ArtifactType.DESUGAR_LIB_MERGED_KEEP_RULES)
                reverseMetadata(FEATURE_PUBLISHED_DEX, ArtifactType.FEATURE_PUBLISHED_DEX)
                reverseMetadata(LINT_MODEL, ArtifactType.LINT_MODEL)
                reverseMetadata(LINT_VITAL_LINT_MODEL, ArtifactType.LINT_VITAL_LINT_MODEL)
                reverseMetadata(LINT_PARTIAL_RESULTS, ArtifactType.LINT_PARTIAL_RESULTS)
                reverseMetadata(LINT_VITAL_PARTIAL_RESULTS, ArtifactType.LINT_VITAL_PARTIAL_RESULTS)

                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)
                runtime(FEATURE_NAME, ArtifactType.FEATURE_NAME)
            }


            variantSpec(VariantTypeImpl.LIBRARY) {
                publish(com.android.build.api.artifact.SingleArtifact.AAR, ArtifactType.AAR)

                source(InternalArtifactType.SOURCE_JAR, ArtifactType.SOURCES_JAR)
                javaDoc(InternalArtifactType.JAVA_DOC_JAR, ArtifactType.JAVA_DOC_JAR)

                api(AIDL_PARCELABLE, ArtifactType.AIDL)
                api(RENDERSCRIPT_HEADERS, ArtifactType.RENDERSCRIPT)
                api(COMPILE_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)
                api(PREFAB_PACKAGE_CONFIGURATION, ArtifactType.PREFAB_PACKAGE_CONFIGURATION)
                api(PREFAB_PACKAGE, ArtifactType.PREFAB_PACKAGE)

                // manifest is published to both to compare and detect provided-only library
                // dependencies.
                output(MERGED_MANIFEST, ArtifactType.MANIFEST)
                output(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                output(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                output(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                        ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                output(FULL_JAR, ArtifactType.JAR)
                /** Published to both api and runtime as consumption behavior depends on
                 * [com.android.build.gradle.options.BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES] */
                output(SYMBOL_LIST_WITH_PACKAGE_NAME, ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)

                runtime(RUNTIME_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)

                // Publish the CLASSES_DIR artifact type with a LibraryElements.CLASSES attribute to
                // match the behavior of the Java library plugin. The LibraryElements attribute will
                // be used for incremental dexing of library subprojects.
                runtime(RUNTIME_LIBRARY_CLASSES_DIR, ArtifactType.CLASSES_DIR, LibraryElements.CLASSES)

                runtime(LIBRARY_ASSETS, ArtifactType.ASSETS)
                runtime(PACKAGED_RES, ArtifactType.ANDROID_RES)
                runtime(PUBLIC_RES, ArtifactType.PUBLIC_RES)
                runtime(COMPILE_SYMBOL_LIST, ArtifactType.COMPILE_SYMBOL_LIST)
                runtime(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES)
                runtime(CONSUMER_PROGUARD_DIR, ArtifactType.UNFILTERED_PROGUARD_RULES)
                runtime(LIBRARY_JNI, ArtifactType.JNI)
                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)
                runtime(COMPILED_LOCAL_RESOURCES, ArtifactType.COMPILED_DEPENDENCIES_RESOURCES)
                runtime(AAR_METADATA, ArtifactType.AAR_METADATA)
                runtime(InternalArtifactType.LIBRARY_ART_PROFILE, ArtifactType.ART_PROFILE)
                // Publish lint artifacts to API_AND_RUNTIME_ELEMENTS to support compileOnly module
                // dependencies.
                output(LINT_PUBLISH_JAR, ArtifactType.LINT)
                output(LINT_MODEL, ArtifactType.LINT_MODEL)
                output(LINT_PARTIAL_RESULTS, ArtifactType.LINT_PARTIAL_RESULTS)
                output(LOCAL_AAR_FOR_LINT, ArtifactType.LOCAL_AAR_FOR_LINT)
                output(LINT_MODEL_METADATA, ArtifactType.LINT_MODEL_METADATA)
            }

            variantSpec(VariantTypeImpl.TEST_FIXTURES) {
                publish(com.android.build.api.artifact.SingleArtifact.AAR, ArtifactType.AAR)

                api(COMPILE_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)

                // manifest is published to both to compare and detect provided-only library
                // dependencies.
                output(MERGED_MANIFEST, ArtifactType.MANIFEST)
                output(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                output(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                output(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                    ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                output(FULL_JAR, ArtifactType.JAR)
                /** Published to both api and runtime as consumption behavior depends on
                 * [com.android.build.gradle.options.BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES] */
                output(SYMBOL_LIST_WITH_PACKAGE_NAME, ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)

                runtime(RUNTIME_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)

                // Publish the CLASSES_DIR artifact type with a LibraryElements.CLASSES attribute to
                // match the behavior of the Java library plugin. The LibraryElements attribute will
                // be used for incremental dexing of test fixtures.
                runtime(RUNTIME_LIBRARY_CLASSES_DIR, ArtifactType.CLASSES_DIR, LibraryElements.CLASSES)

                runtime(LIBRARY_ASSETS, ArtifactType.ASSETS)
                runtime(PACKAGED_RES, ArtifactType.ANDROID_RES)
                runtime(PUBLIC_RES, ArtifactType.PUBLIC_RES)
                runtime(COMPILE_SYMBOL_LIST, ArtifactType.COMPILE_SYMBOL_LIST)
                runtime(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES)
                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)
                runtime(COMPILED_LOCAL_RESOURCES, ArtifactType.COMPILED_DEPENDENCIES_RESOURCES)
                runtime(AAR_METADATA, ArtifactType.AAR_METADATA)
                // Publish LOCAL_AAR_FOR_LINT to API_AND_RUNTIME_ELEMENTS to support compileOnly
                // module dependencies.
                output(LOCAL_AAR_FOR_LINT, ArtifactType.LOCAL_AAR_FOR_LINT)
            }

            // Publishing will be done manually from the lint standalone plugin for now.
            // Eventually we should just unify the infrastructure to declare the publications here.
            variantSpec(VariantTypeImpl.JAVA_LIBRARY)
            // empty specs
            variantSpec(VariantTypeImpl.TEST_APK)
            variantSpec(VariantTypeImpl.ANDROID_TEST)
            variantSpec(VariantTypeImpl.UNIT_TEST)

            lock()
        }

        @JvmStatic
        fun getVariantSpec(variantType: VariantType): VariantSpec {
            return variantMap[variantType]!!
        }

        @JvmStatic
        internal fun getVariantMap(): Map<VariantType, VariantSpec> {
            return variantMap
        }

        private fun lock() {
            variantMap = builder!!.build()
            builder = null
        }

        private fun variantSpec(
                variantType: VariantType,
                action: VariantSpecBuilder.() -> Unit) {
            val specBuilder = instantiateSpecBuilder(variantType)

            action(specBuilder)
            builder!!.put(variantType, specBuilder.toSpec())
        }

        private fun variantSpec(variantType: VariantType) {
            builder!!.put(variantType, instantiateSpecBuilder(variantType).toSpec())
        }

        private fun instantiateSpecBuilder(variantType: VariantType) =
            when (variantType) {
                VariantTypeImpl.BASE_APK -> AppVariantSpecBuilder(variantType)
                VariantTypeImpl.LIBRARY -> LibraryVariantSpecBuilder(variantType)
                VariantTypeImpl.TEST_FIXTURES -> TestFixturesVariantSpecBuilder(variantType)
                else -> VariantSpecBuilderImpl(variantType)
            }
    }

    interface VariantSpecBuilder {
        val variantType: VariantType

        fun output(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
        fun api(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
        fun runtime(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType, libraryElements: String? = null)
        fun reverseMetadata(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
        fun publish(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
        fun source(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
        fun javaDoc(taskOutputType: Artifact.Single<out FileSystemLocation>, artifactType: ArtifactType)
    }
}

private val API_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(API_ELEMENTS)
private val RUNTIME_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(RUNTIME_ELEMENTS)
private val API_AND_RUNTIME_ELEMENTS: ImmutableList<PublishedConfigType> = ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS)
private val REVERSE_METADATA_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(
    REVERSE_METADATA_ELEMENTS)
private val API_AND_RUNTIME_PUBLICATION: ImmutableList<PublishedConfigType> =
    ImmutableList.of(API_PUBLICATION, RUNTIME_PUBLICATION)
private val SOURCE_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.SOURCE_PUBLICATION
)
private val JAVA_DOC_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.JAVA_DOC_PUBLICATION
)
private val APK_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.APK_PUBLICATION)
private val AAB_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.AAB_PUBLICATION)

// --- Implementation of the public Spec interfaces

private class VariantPublishingSpecImpl(
        override val variantType: VariantType,
        private val parentSpec: PublishingSpecs.VariantSpec?,
        override val outputs: Set<PublishingSpecs.OutputSpec>
) : PublishingSpecs.VariantSpec {

    private var _artifactMap: Map<ArtifactType, List<PublishingSpecs.OutputSpec>>? = null

    private val artifactMap: Map<ArtifactType, List<PublishingSpecs.OutputSpec>>
        get() {
            val map = _artifactMap
            return if (map == null) {
                val map2 = outputs.groupBy { it.artifactType }
                _artifactMap = map2
                map2
            } else {
                map
            }
        }

    override fun getSpec(
        artifactType: ArtifactType,
        publishConfigType: PublishedConfigType?
    ): PublishingSpecs.OutputSpec? {
        return artifactMap[artifactType]?.let {specs ->
            if (specs.size <= 1) {
                specs.singleOrNull()
            } else {
                val matchingSpecs = if (publishConfigType != null) {
                    specs.filter { it.publishedConfigTypes.contains(publishConfigType) }
                } else {
                    specs
                }
                if (matchingSpecs.size > 1) {
                    throw IllegalStateException("Multiple output specs found for $artifactType and $publishConfigType")
                } else {
                    matchingSpecs.singleOrNull()
                }
            }
        } ?: parentSpec?.getSpec(artifactType, publishConfigType)
    }
}

private data class OutputSpecImpl(
        override val outputType: Artifact.Single<out FileSystemLocation>,
        override val artifactType: ArtifactType,
        override val publishedConfigTypes: ImmutableList<PublishedConfigType> = API_AND_RUNTIME_ELEMENTS,
        override val libraryElements: String? = null
) : PublishingSpecs.OutputSpec

// -- Implementation of the internal Spec Builder interfaces

private open class VariantSpecBuilderImpl (
        override val variantType: VariantType): PublishingSpecs.VariantSpecBuilder {

    protected val outputs = mutableSetOf<PublishingSpecs.OutputSpec>()

    override fun output(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType))
    }

    override fun api(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, API_ELEMENTS_ONLY))
    }

    override fun runtime(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType, libraryElements: String?) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, RUNTIME_ELEMENTS_ONLY, libraryElements))
    }

    override fun reverseMetadata(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        if (!variantType.publishToMetadata) {
            throw RuntimeException("VariantType '$variantType' does not support metadata publishing")
        }
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, REVERSE_METADATA_ELEMENTS_ONLY))
    }

    override fun publish(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        throw RuntimeException("This VariantSpecBuilder does not support publish. VariantType is $variantType")
    }

    override fun source(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        throw RuntimeException("This VariantSpecBuilder does not support source. VariantType is $variantType")
    }

    override fun javaDoc(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        throw RuntimeException("This VariantSpecBuilder does not support javaDoc. VariantType is $variantType")
    }

    fun toSpec(parentSpec: PublishingSpecs.VariantSpec? = null): PublishingSpecs.VariantSpec {
        return VariantPublishingSpecImpl(
                variantType,
                parentSpec,
                outputs.toImmutableSet())
    }
}

private class TestFixturesVariantSpecBuilder(variantType: VariantType):
    VariantSpecBuilderImpl(variantType) {

    override fun publish(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, API_AND_RUNTIME_PUBLICATION))
    }
}

private class LibraryVariantSpecBuilder(variantType: VariantType): VariantSpecBuilderImpl(variantType) {

    override fun publish(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, API_AND_RUNTIME_PUBLICATION))
    }

    override fun source(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, SOURCE_PUBLICATION))
    }

    override fun javaDoc(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, JAVA_DOC_PUBLICATION))
    }
}

private class AppVariantSpecBuilder(variantType: VariantType): VariantSpecBuilderImpl(variantType) {

    override fun publish(taskOutputType: Artifact.Single<*>, artifactType: ArtifactType) {
        if (artifactType == ArtifactType.BUNDLE) {
            outputs.add(OutputSpecImpl(taskOutputType, artifactType, AAB_PUBLICATION))
        } else {
            outputs.add(OutputSpecImpl(taskOutputType, artifactType, APK_PUBLICATION))
        }
    }
}
