/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.creationconfig

import com.android.build.api.dsl.CompileOptions
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.tasks.DEFAULT_INCREMENTAL_COMPILATION
import com.android.build.gradle.tasks.getAnnotationProcessorJars
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternSet

interface JavaCompileCreationConfig : TaskCreationConfig {

    val source: FileTree

    // Flags
    val isExportDataBindingClassList: Boolean
    val isIncremental: Boolean
    val dataBinding: Boolean
    val usingKapt: Boolean
    val useBuiltInKaptSupport: Boolean

    val sourceCompatibility: String?
    val targetCompatibility: String?

    val compileSdkHashString: String
    val compileOptions: CompileOptions
    val annotationProcessor: AnnotationProcessor

    val bootClasspath: Provider<List<RegularFile>>
    val compileClasspath: FileCollection
    val builtInKotlincOutput: Provider<Directory>?
    val builtInKaptArtifact: Provider<Directory>?
    val annotationProcessorPath: FileCollection?

}

fun createJavaCompileConfig(
    creationConfig: ComponentCreationConfig,
    usingKapt: Boolean
): JavaCompileCreationConfig {
    return if (creationConfig is KmpComponentCreationConfig) {
        object : BaseJavaCompileCreationConfig(creationConfig, usingKapt) {
            @SuppressWarnings("EagerGradleConfiguration")
            private fun KmpComponentCreationConfig.getKotlinJvmTarget() =
                androidKotlinCompilation.compileTaskProvider.get()
                    .compilerOptions.jvmTarget.orNull?.target

            override val sourceCompatibility: String?
                get() = creationConfig.getKotlinJvmTarget()

            override val targetCompatibility: String?
                get() = creationConfig.getKotlinJvmTarget()

            override val annotationProcessorPath: FileCollection? = null
        }
    } else {
        BaseJavaCompileCreationConfig(creationConfig, usingKapt)
    }
}

internal open class BaseJavaCompileCreationConfig(
    val creationConfig: ComponentCreationConfig,
    override val usingKapt: Boolean
) : JavaCompileCreationConfig, TaskCreationConfig by creationConfig {

    override val source: FileTree
        get() = computeJavaSourceWithoutDependencies(creationConfig)

    // Flags
    override val isExportDataBindingClassList: Boolean
        get() = creationConfig.componentType.isExportDataBindingClassList
    override val isIncremental: Boolean
        get() = creationConfig.global.compileOptionsIncremental ?: DEFAULT_INCREMENTAL_COMPILATION
    override val dataBinding: Boolean
        get() = creationConfig.buildFeatures.dataBinding
    override val useBuiltInKaptSupport: Boolean
        get() = creationConfig.useBuiltInKaptSupport

    override val sourceCompatibility: String?
        get() = compileOptions.sourceCompatibility.toString()
    override val targetCompatibility: String?
        get() = compileOptions.targetCompatibility.toString()

    override val compileSdkHashString: String
        get() = creationConfig.global.compileSdkHashString
    override val compileOptions: CompileOptions
        get() = creationConfig.global.compileOptions
    override val annotationProcessor: AnnotationProcessor
        get() = creationConfig.javaCompilation.annotationProcessor

    override val bootClasspath: Provider<List<RegularFile>>
        get() = creationConfig.global.bootClasspath
    override val compileClasspath: FileCollection
        get() = creationConfig.compileClasspath
    override val builtInKotlincOutput: Provider<Directory>?
        get() = creationConfig.getBuiltInKotlincOutput()
    override val builtInKaptArtifact: Provider<Directory>?
        get() = creationConfig.getBuiltInKaptArtifact(InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR)
    override val annotationProcessorPath: FileCollection?
        get() = creationConfig.getAnnotationProcessorJars()

}

fun computeJavaSourceWithoutDependencies(creationConfig: ComponentCreationConfig): FileTree {
    // Include only java sources, otherwise we hit b/144249620.
    val javaSourcesFilter = PatternSet().include("**/*.java")
    return creationConfig.services.fileCollection().also { fileCollection ->
        // do not resolve the provider before execution phase, b/117161463.
        creationConfig.sources.java { javaSources ->
            // the KAPT plugin is looking up the JavaCompile.sources and resolving it at
            // configuration time which requires us to pass the old variant API version.
            // see b/259343260
            fileCollection.from(javaSources.getAsFileTreesForOldVariantAPI())
        }
        creationConfig.getBuiltInKaptArtifact(InternalArtifactType.BUILT_IN_KAPT_GENERATED_JAVA_SOURCES)
            ?.let { fileCollection.from(it) }
    }.asFileTree.matching(javaSourcesFilter)
}
