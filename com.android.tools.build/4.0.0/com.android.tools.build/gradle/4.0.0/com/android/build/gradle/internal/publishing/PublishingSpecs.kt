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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_API_PUBLICATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_RUNTIME_PUBLICATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_PUBLICATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.REVERSE_METADATA_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION
import com.android.build.gradle.internal.scope.AnchorOutputType.ALL_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.AAPT_PROGUARD_FILE
import com.android.build.gradle.internal.scope.InternalArtifactType.AIDL_PARCELABLE
import com.android.build.gradle.internal.scope.InternalArtifactType.APK
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_ZIP
import com.android.build.gradle.internal.scope.InternalArtifactType.APP_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.BASE_MODULE_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILED_LOCAL_RESOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.CONSUMER_PROGUARD_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DEFINED_ONLY_SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_NAME
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_SET_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.FULL_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JNI
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PUBLISH_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.NAVIGATION_JSON
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_DEPENDENCIES
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLIC_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_HEADERS
import com.android.build.gradle.internal.scope.InternalArtifactType.RES_STATIC_LIBRARY
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.SIGNING_CONFIG
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
import com.android.build.gradle.internal.scope.SingleArtifactType
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
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
 *
 *
 * This mapping also offers reverse mapping override for tests (per [VariantType] as well),
 * allowing a test variant to not use exactly the published artifact of the tested variant but a
 * different version. This allows for instance the unit tests of libraries to use the full Java
 * classes, including the R class for unit testing, while the published artifact does not contain
 * the R class. Similarly, the override can extend the published scope (api vs runtime), which is
 * needed to run the unit tests.
 */
class PublishingSpecs {

    /**
     * The publishing spec for a variant
     */
    interface VariantSpec {
        val variantType: VariantType
        val outputs: Set<OutputSpec>
        val testingSpecs: Map<VariantType, VariantSpec>

        fun getTestingSpec(variantType: VariantType): VariantSpec

        fun getSpec(artifactType: ArtifactType, publishConfigType: PublishedConfigType?): OutputSpec?
    }

    /**
     * A published output
     */
    interface OutputSpec {
        val outputType: SingleArtifactType<out FileSystemLocation>
        val artifactType: ArtifactType
        val publishedConfigTypes: ImmutableList<PublishedConfigType>
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
                api(APP_CLASSES, ArtifactType.CLASSES_JAR)
                api(APK_MAPPING, ArtifactType.APK_MAPPING)

                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)

                // FIXME: need data binding artifacts as well for Dynamic apps.

                runtime(APK, ArtifactType.APK)
                publish(APK_ZIP, ArtifactType.APK_ZIP)

                runtime(InternalArtifactType.APKS_FROM_BUNDLE, ArtifactType.APKS_FROM_BUNDLE)
                runtime(PACKAGED_DEPENDENCIES, ArtifactType.PACKAGED_DEPENDENCIES)

                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)

                // output of bundle-tool
                publish(BUNDLE, ArtifactType.BUNDLE)

                // this is only for base modules.
                api(FEATURE_SET_METADATA, ArtifactType.FEATURE_SET_METADATA)
                api(BASE_MODULE_METADATA, ArtifactType.BASE_MODULE_METADATA)
                api(SIGNING_CONFIG, ArtifactType.FEATURE_SIGNING_CONFIG)

                // ----

                testSpec(VariantTypeImpl.ANDROID_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping
                    api(APP_CLASSES, ArtifactType.CLASSES_JAR)
                }

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping. Also add it to the runtime as it's
                    // needed to run the tests!
                    output(ALL_CLASSES, ArtifactType.CLASSES_JAR)
                    // JAVA_RES isn't published by the app, but we need it for the unit tests
                    output(JAVA_RES, ArtifactType.JAVA_RES)
                }
            }

            variantSpec(VariantTypeImpl.OPTIONAL_APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                api(APP_CLASSES, ArtifactType.CLASSES_JAR)
                api(APK_MAPPING, ArtifactType.APK_MAPPING)

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

                reverseMetadata(DESUGAR_LIB_PROJECT_KEEP_RULES, ArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES)
                reverseMetadata(DESUGAR_LIB_SUBPROJECT_KEEP_RULES, ArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES)
                reverseMetadata(DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES, ArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES)
                reverseMetadata(DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES, ArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES)
                reverseMetadata(DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES, ArtifactType.DESUGAR_LIB_EXTERNAL_FILE_KEEP_RULES)

                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)
                runtime(FEATURE_NAME, ArtifactType.FEATURE_NAME)

                // ----

                testSpec(VariantTypeImpl.ANDROID_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping
                    api(APP_CLASSES, ArtifactType.CLASSES_JAR)
                }

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping. Also add it to the runtime as it's
                    // needed to run the tests!
                    output(ALL_CLASSES, ArtifactType.CLASSES_JAR)
                    // JAVA_RES isn't published by the app, but we need it for the unit tests
                    output(JAVA_RES, ArtifactType.JAVA_RES)
                }
            }


            variantSpec(VariantTypeImpl.LIBRARY) {
                publish(InternalArtifactType.AAR, ArtifactType.AAR)

                api(COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                        ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                api(AIDL_PARCELABLE, ArtifactType.AIDL)
                api(RENDERSCRIPT_HEADERS, ArtifactType.RENDERSCRIPT)
                api(COMPILE_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)

                // manifest is published to both to compare and detect provided-only library
                // dependencies.
                output(LIBRARY_MANIFEST, ArtifactType.MANIFEST)
                output(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                output(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                output(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                        ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                output(FULL_JAR, ArtifactType.JAR)
                /** Published to both api and runtime as consumption behavior depends on
                 * [com.android.build.gradle.options.BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES] */
                output(SYMBOL_LIST_WITH_PACKAGE_NAME, ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)

                runtime(RUNTIME_LIBRARY_CLASSES_JAR, ArtifactType.CLASSES_JAR)
                runtime(RUNTIME_LIBRARY_CLASSES_DIR, ArtifactType.CLASSES_DIR)
                runtime(LIBRARY_ASSETS, ArtifactType.ASSETS)
                runtime(PACKAGED_RES, ArtifactType.ANDROID_RES)
                runtime(PUBLIC_RES, ArtifactType.PUBLIC_RES)
                runtime(COMPILE_SYMBOL_LIST, ArtifactType.COMPILE_SYMBOL_LIST)
                runtime(DEFINED_ONLY_SYMBOL_LIST, ArtifactType.DEFINED_ONLY_SYMBOL_LIST)
                runtime(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES)
                runtime(CONSUMER_PROGUARD_DIR, ArtifactType.UNFILTERED_PROGUARD_RULES)
                runtime(LIBRARY_JNI, ArtifactType.JNI)
                runtime(LINT_PUBLISH_JAR, ArtifactType.LINT)
                runtime(NAVIGATION_JSON, ArtifactType.NAVIGATION_JSON)
                runtime(COMPILED_LOCAL_RESOURCES, ArtifactType.COMPILED_DEPENDENCIES_RESOURCES)

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // unit test need ALL_CLASSES instead of RUNTIME_LIBRARY_CLASSES to get
                    // access to the R class. Also scope should be API+Runtime.
                    output(ALL_CLASSES, ArtifactType.CLASSES_JAR)
                }
            }

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
                else -> VariantSpecBuilderImpl(variantType)
            }
    }

    interface VariantSpecBuilder {
        val variantType: VariantType

        fun output(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType)
        fun api(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType)
        fun runtime(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType)
        fun reverseMetadata(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType)
        fun publish(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType)

        fun testSpec(variantType: VariantType, action: VariantSpecBuilder.() -> Unit)
    }
}

private val API_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(API_ELEMENTS)
private val RUNTIME_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(RUNTIME_ELEMENTS)
private val API_AND_RUNTIME_ELEMENTS: ImmutableList<PublishedConfigType> = ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS)
private val REVERSE_METADATA_ELEMENTS_ONLY: ImmutableList<PublishedConfigType> = ImmutableList.of(
    REVERSE_METADATA_ELEMENTS)
private val API_AND_RUNTIME_PUBLICATION: ImmutableList<PublishedConfigType> =
    ImmutableList.of(API_PUBLICATION, RUNTIME_PUBLICATION, ALL_API_PUBLICATION, ALL_RUNTIME_PUBLICATION)
private val APK_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.APK_PUBLICATION)
private val AAB_PUBLICATION: ImmutableList<PublishedConfigType> = ImmutableList.of(
    PublishedConfigType.AAB_PUBLICATION)

// --- Implementation of the public Spec interfaces

private class VariantPublishingSpecImpl(
        override val variantType: VariantType,
        private val parentSpec: PublishingSpecs.VariantSpec?,
        override val outputs: Set<PublishingSpecs.OutputSpec>,
        testingSpecBuilders: Map<VariantType, VariantSpecBuilderImpl>
) : PublishingSpecs.VariantSpec {

    override val testingSpecs: Map<VariantType, PublishingSpecs.VariantSpec>
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

    init {
        testingSpecs = testingSpecBuilders.toImmutableMap { it.toSpec(this) }
    }

    override fun getTestingSpec(variantType: VariantType): PublishingSpecs.VariantSpec {
        Preconditions.checkState(variantType.isTestComponent)

        val testingSpec = testingSpecs[variantType]
        return testingSpec ?: this
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
        override val outputType: SingleArtifactType<*>,
        override val artifactType: ArtifactType,
        override val publishedConfigTypes: ImmutableList<PublishedConfigType> = API_AND_RUNTIME_ELEMENTS) : PublishingSpecs.OutputSpec

// -- Implementation of the internal Spec Builder interfaces

private open class VariantSpecBuilderImpl (
        override val variantType: VariantType): PublishingSpecs.VariantSpecBuilder {

    protected val outputs = mutableSetOf<PublishingSpecs.OutputSpec>()
    private val testingSpecs = mutableMapOf<VariantType, VariantSpecBuilderImpl>()

    override fun output(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType))
    }

    override fun api(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, API_ELEMENTS_ONLY))
    }

    override fun runtime(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, RUNTIME_ELEMENTS_ONLY))
    }

    override fun reverseMetadata(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        if (!variantType.publishToMetadata) {
            throw RuntimeException("VariantType '$variantType' does not support metadata publishing")
        }
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, REVERSE_METADATA_ELEMENTS_ONLY))
    }

    override fun publish(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        throw RuntimeException("This VariantSpecBuilder does not support publish. VariantType is $variantType")
    }

    override fun testSpec(
            variantType: VariantType,
            action: PublishingSpecs.VariantSpecBuilder.() -> Unit) {
        Preconditions.checkState(!this.variantType.isForTesting)
        Preconditions.checkState(variantType.isTestComponent)
        Preconditions.checkState(!testingSpecs.containsKey(variantType))

        val specBuilder = VariantSpecBuilderImpl(
                variantType)
        action(specBuilder)

        testingSpecs[variantType] = specBuilder
    }

    fun toSpec(parentSpec: PublishingSpecs.VariantSpec? = null): PublishingSpecs.VariantSpec {
        return VariantPublishingSpecImpl(
                variantType,
                parentSpec,
                outputs.toImmutableSet(),
                testingSpecs)
    }
}

private class LibraryVariantSpecBuilder(variantType: VariantType): VariantSpecBuilderImpl(variantType) {

    override fun publish(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        outputs.add(OutputSpecImpl(taskOutputType, artifactType, API_AND_RUNTIME_PUBLICATION))
    }
}

private class AppVariantSpecBuilder(variantType: VariantType): VariantSpecBuilderImpl(variantType) {

    override fun publish(taskOutputType: SingleArtifactType<*>, artifactType: ArtifactType) {
        if (artifactType == ArtifactType.BUNDLE) {
            outputs.add(OutputSpecImpl(taskOutputType, artifactType, AAB_PUBLICATION))
        } else {
            outputs.add(OutputSpecImpl(taskOutputType, artifactType, APK_PUBLICATION))
        }
    }
}
