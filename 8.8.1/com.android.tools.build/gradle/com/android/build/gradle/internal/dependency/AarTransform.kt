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

package com.android.build.gradle.internal.dependency

import android.databinding.tool.DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR
import android.databinding.tool.DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_AIDL
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FD_JNI
import com.android.SdkConstants.FD_PREFAB_PACKAGE
import com.android.SdkConstants.FD_RENDERSCRIPT
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.SdkConstants.FN_ART_PROFILE
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_LINT_JAR
import com.android.SdkConstants.FN_NAMESPACED_SHARED_LIBRARY_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_NAVIGATION_JSON
import com.android.SdkConstants.FN_PROGUARD_TXT
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.SdkConstants.LIBS_FOLDER
import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.build.gradle.internal.dependency.ExtractProGuardRulesTransform.Companion.performTransform
import com.android.build.gradle.internal.publishing.AarOrJarTypeToConsume
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AAR_METADATA
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANNOTATIONS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ART_PROFILE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ASSETS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.COMPILE_SYMBOL_LIST
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.LINT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.NAVIGATION_JSON
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PUBLIC_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RENDERSCRIPT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RES_SHARED_OEM_TOKEN_LIBRARY
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SHARED_ASSETS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SHARED_CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SHARED_JAVA_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SHARED_JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PATH_SHARED_LIBRARY_RESOURCES_APK
import com.android.build.gradle.internal.tasks.AarMetadataTask
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault
import java.io.File

/** Transform that returns the content of an extracted AAR folder. */
@DisableCachingByDefault(because = DisabledCachingReason.FAST_TRANSFORM)
abstract class AarTransform : TransformAction<AarTransform.Parameters> {

    interface Parameters : GenericTransformParameters {

        @get:Input
        val targetType: Property<ArtifactType>

        @get:Input
        val namespacedSharedLibSupport: Property<Boolean>
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val extractedAarDir = inputArtifact.get().asFile

        fun outputIfExists(relativePath: String) {
            val input = extractedAarDir.resolve(relativePath)
            when {
                input.isDirectory -> transformOutputs.dir(input)
                input.isFile -> transformOutputs.file(input)
            }
        }

        when (val targetType = parameters.targetType.get()) {
            CLASSES_JAR,
            JAVA_RES,
            JAR,
            PROCESSED_JAR -> {
                // even though resources are supposed to only be in the main jar of the AAR, this
                // is not necessarily enforced by all build systems generating AAR so it's safer to
                // read all jars from the manifest.
                // For namespaced shared libraries, these are provided via SHARED_CLASSES and
                // SHARED_JAVA_RES.
                if (!isNamespacedSharedLibrary()) {
                    getJars(extractedAarDir).forEach { transformOutputs.file(it) }
                }
            }

            SHARED_CLASSES,
            SHARED_JAVA_RES -> {
                if (isNamespacedSharedLibrary()) {
                    getJars(extractedAarDir).forEach { transformOutputs.file(it) }
                }
            }

            LINT -> outputIfExists("$FD_JARS/$FN_LINT_JAR")
            MANIFEST -> {
                // Return both the manifest and the extra snippet for the namespaced shared library.
                outputIfExists(FN_ANDROID_MANIFEST_XML)
                if (isNamespacedSharedLibrary()) {
                    outputIfExists(FN_NAMESPACED_SHARED_LIBRARY_ANDROID_MANIFEST_XML)
                }
            }

            ANDROID_RES -> outputIfExists(FD_RES)
            ASSETS -> outputIfExists(FD_ASSETS)
            JNI -> outputIfExists(FD_JNI)
            AIDL -> outputIfExists(FD_AIDL)
            RENDERSCRIPT -> outputIfExists(FD_RENDERSCRIPT)
            UNFILTERED_PROGUARD_RULES -> {
                if (!performTransform(extractedAarDir.resolve("$FD_JARS/$FN_CLASSES_JAR"), transformOutputs, false)) {
                    outputIfExists(FN_PROGUARD_TXT)
                }
            }

            ANNOTATIONS -> outputIfExists(FN_ANNOTATIONS_ZIP)
            PUBLIC_RES -> outputIfExists(FN_PUBLIC_TXT)
            COMPILE_SYMBOL_LIST -> outputIfExists(FN_RESOURCE_TEXT)
            RES_STATIC_LIBRARY -> {
                if (!isNamespacedSharedLibrary()) {
                    outputIfExists(FN_RESOURCE_STATIC_LIBRARY)
                }
            }

            RES_SHARED_STATIC_LIBRARY -> {
                if (isNamespacedSharedLibrary()) {
                    outputIfExists(FN_RESOURCE_SHARED_STATIC_LIBRARY)
                }
            }

            RES_SHARED_OEM_TOKEN_LIBRARY -> outputIfExists(PATH_SHARED_LIBRARY_RESOURCES_APK)
            DATA_BINDING_ARTIFACT -> outputIfExists(DATA_BINDING_ROOT_FOLDER_IN_AAR)
            DATA_BINDING_BASE_CLASS_LOG_ARTIFACT -> outputIfExists(DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR)
            PREFAB_PACKAGE -> outputIfExists(FD_PREFAB_PACKAGE)
            AAR_METADATA -> outputIfExists(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            ART_PROFILE -> outputIfExists(FN_ART_PROFILE)
            NAVIGATION_JSON -> outputIfExists(FN_NAVIGATION_JSON)
            else -> error("Unsupported type in AarTransform: $targetType")
        }
    }

    private fun isNamespacedSharedLibrary(): Boolean {
        return parameters.namespacedSharedLibSupport.get()
                && inputArtifact.get().asFile.resolve(FN_NAMESPACED_SHARED_LIBRARY_ANDROID_MANIFEST_XML).isFile
    }

    companion object {

        fun getTransformTargets(
            aarOrJarTypeToConsume: AarOrJarTypeToConsume,
            sharedLibSupportEnabled: Boolean
        ): List<ArtifactType> {
            return listOfNotNull(
                aarOrJarTypeToConsume.jar,
                SHARED_CLASSES,
                JAVA_RES,
                SHARED_JAVA_RES,
                MANIFEST,
                ANDROID_RES,
                ASSETS,
                SHARED_ASSETS,
                JNI,
                SHARED_JNI,
                AIDL,
                RENDERSCRIPT,
                UNFILTERED_PROGUARD_RULES,
                LINT,
                ANNOTATIONS,
                PUBLIC_RES,
                COMPILE_SYMBOL_LIST,
                DATA_BINDING_ARTIFACT,
                DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                RES_STATIC_LIBRARY,
                RES_SHARED_STATIC_LIBRARY,
                PREFAB_PACKAGE,
                AAR_METADATA,
                ART_PROFILE,
                NAVIGATION_JSON,
                RES_SHARED_OEM_TOKEN_LIBRARY.takeIf { sharedLibSupportEnabled }
            )
        }
    }
}

private fun getJars(extractedAarDir: File): List<File> {
    val allJars = mutableListOf<File>()

    // Get classes.jar
    val jarsDir = extractedAarDir.resolve(FD_JARS)
    val classesJar = jarsDir.resolve(FN_CLASSES_JAR)
    if (classesJar.isFile) {
        allJars.add(classesJar)
    }

    // Get local jars in jars/libs
    val localJarsDir = jarsDir.resolve(LIBS_FOLDER)
    if (localJarsDir.isDirectory) {
        val localJars = localJarsDir
            .listFiles { file: File -> file.path.endsWith(DOT_JAR) }!!.toList()
            .sortedBy { it.invariantSeparatorsPath }
        allJars.addAll(localJars)
    }

    return allJars
}
