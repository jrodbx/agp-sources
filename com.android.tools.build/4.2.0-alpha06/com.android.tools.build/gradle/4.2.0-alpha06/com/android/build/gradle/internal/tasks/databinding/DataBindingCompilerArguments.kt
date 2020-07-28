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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.CompilerArguments
import com.android.build.api.artifact.impl.DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DATA_BINDING_BASE_FEATURE_INFO
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DATA_BINDING_FEATURE_INFO
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Arguments passed to data binding. This class mimics the [CompilerArguments] class except that it
 * also implements [CommandLineArgumentProvider] for input/output annotations.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataBindingCompilerArguments constructor(

    @get:Input
    val incremental: Boolean,

    @get:Input
    val artifactType: CompilerArguments.Type,

    @get:Input
    val packageName: Provider<String>,

    @get:Input
    val minApi: Int,

    // We can't set the sdkDir as an @InputDirectory because it is too large to compute a hash. We
    // can't set it as an @Input either because it would break cache relocatability. Therefore, we
    // annotate it with @Internal, expecting that the directory's contents should be stable and this
    // won't affect correctness.
    @get:Internal
    val sdkDir: File,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val dependencyArtifactsDir: Provider<Directory>,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val layoutInfoDir: Provider<Directory>,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val classLogDir: Provider<Directory>,

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val baseFeatureInfoDir: Provider<Directory>,

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val featureInfoDir: Provider<Directory>,

    @get:OutputDirectory
    val aarOutDir: Provider<Directory>,

    @get:Optional
    @get:OutputFile
    val exportClassListOutFile: Provider<RegularFile>,

    @get:Input
    val enableDebugLogs: Boolean,

    // We don't set this as an @Input because: (1) it doesn't affect the results of data binding
    // processing, and (2) its value is changed between an Android Studio build and a command line
    // build; by not setting it as @Input, we allow the users to get incremental/UP-TO-DATE builds
    // when switching between the two modes (see https://issuetracker.google.com/80555723).
    @get:Internal
    val printEncodedErrorLogs: Boolean,

    @get:Input
    val isTestVariant: Boolean,

    @get:Input
    val isEnabledForTests: Boolean,

    @get:Input
    val isEnableV2: Boolean
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        val arguments = CompilerArguments(
            incremental = incremental,
            artifactType = artifactType,
            modulePackage = packageName.get(),
            minApi = minApi,
            sdkDir = sdkDir,
            dependencyArtifactsDir = dependencyArtifactsDir.get().asFile,
            layoutInfoDir = layoutInfoDir.get().asFile,
            classLogDir = classLogDir.get().asFile,
            baseFeatureInfoDir = baseFeatureInfoDir.orNull?.asFile,
            featureInfoDir = featureInfoDir.orNull?.asFile,
            aarOutDir = aarOutDir.get().asFile,
            exportClassListOutFile = exportClassListOutFile.orNull?.asFile,
            enableDebugLogs = enableDebugLogs,
            printEncodedErrorLogs = printEncodedErrorLogs,
            isTestVariant = isTestVariant,
            isEnabledForTests = isEnabledForTests,
            isEnableV2 = isEnableV2
        ).toMap()

        // Don't need to sort the returned list as the order shouldn't matter to Gradle.
        // Also don't need to escape the key and value strings as they will be passed as-is to
        // the Java compiler.
        return arguments.map { entry -> "-A${entry.key}=${entry.value}" }
    }

    companion object {

        @JvmStatic
        fun createArguments(
            creationConfig: BaseCreationConfig,
            enableDebugLogs: Boolean,
            printEncodedErrorLogs: Boolean
        ): DataBindingCompilerArguments {
            val globalScope = creationConfig.globalScope
            val artifacts = creationConfig.artifacts

            return DataBindingCompilerArguments(
                incremental = creationConfig.services.projectOptions
                    .get(BooleanOption.ENABLE_INCREMENTAL_DATA_BINDING),
                artifactType = getModuleType(creationConfig),
                packageName = creationConfig.packageName,
                minApi = creationConfig.minSdkVersion.apiLevel,
                sdkDir = globalScope.sdkComponents.flatMap { it.sdkDirectoryProvider }.get().asFile,
                dependencyArtifactsDir = artifacts.get(DATA_BINDING_DEPENDENCY_ARTIFACTS),
                layoutInfoDir = artifacts.get(getLayoutInfoArtifactType(creationConfig)),
                classLogDir = artifacts.get(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                baseFeatureInfoDir = artifacts.get(
                    FEATURE_DATA_BINDING_BASE_FEATURE_INFO
                ),
                featureInfoDir = artifacts.get(FEATURE_DATA_BINDING_FEATURE_INFO),
                // Note that aarOurDir and exportClassListOutFile below are outputs. In the usual
                // pattern, they need to be wired as producers of the corresponding artifacts
                // through AGP Artifacts API. However, since the actual task that will produce these
                // artifacts is not known at this point (it could be either JavaCompile or Kapt),
                // using the Artifacts API is not possible.
                //
                // Instead, we wire them when JavaCompile or Kapt is registered, and here we'll just
                // get the artifacts' locations.
                aarOutDir = artifacts.get(DATA_BINDING_ARTIFACT),
                exportClassListOutFile = artifacts.get(DATA_BINDING_EXPORT_CLASS_LIST),
                enableDebugLogs = enableDebugLogs,
                printEncodedErrorLogs = printEncodedErrorLogs,
                isTestVariant = creationConfig.variantType.isTestComponent,
                isEnabledForTests = globalScope.extension.dataBinding.isEnabledForTests,
                isEnableV2 = true
            )
        }

        /**
         * Returns the module type of a variant. If it is a testing variant, return the module type
         * of the tested variant.
         */
        @JvmStatic
        fun getModuleType(creationConfig: BaseCreationConfig): CompilerArguments.Type {
            val component = creationConfig.onTestedConfig {
                it
            } ?: creationConfig

            return if (component.variantType.isAar) {
                CompilerArguments.Type.LIBRARY
            } else {
                if (component.variantType.isBaseModule) {
                    CompilerArguments.Type.APPLICATION
                } else {
                    CompilerArguments.Type.FEATURE
                }
            }
        }

        /**
         * Returns the appropriate artifact type of the layout info directory so that it does not
         * trigger unnecessary computations (see bug 133092984 and 110412851).
         */
        @JvmStatic
        fun getLayoutInfoArtifactType(creationConfig: BaseCreationConfig): InternalArtifactType<Directory> {
            return if (creationConfig.variantType.isAar) {
                DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE
            } else {
                DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
            }
        }
    }
}