/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.builder.dexing.KeepRulesConfig
import com.android.builder.dexing.runL8
import com.android.tools.r8.OutputMode
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/**
 * Transform desugar lib jar into a desugared version using L8. This desugared desugar lib jar is in
 * classfile format, which is used by trace reference tool to generate keep rules for shrinking the
 * desugar lib jar into dex format.
 */
@CacheableTransform
abstract class L8DesugarLibTransform : TransformAction<L8DesugarLibTransform.Parameters> {
    interface Parameters: GenericTransformParameters {
        @get:Input
        val libConfiguration: Property<String>
        @get:Input
        val minSdkVersion: Property<Int>
        @get:Classpath
        val fullBootClasspath: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    @get:Classpath
    @get:InputArtifactDependencies
    abstract val dependencies: FileCollection

    override fun transform(outputs: TransformOutputs) {
        val inputFiles = mutableListOf(primaryInput.get().asFile.toPath())
        inputFiles.addAll(dependencies.files.map { it.toPath() })
        val outputDir = outputs.dir(primaryInput.get().asFile.nameWithoutExtension)

        runL8(
            inputFiles,
            outputDir.toPath(),
            parameters.libConfiguration.get(),
            parameters.fullBootClasspath.files.map { it.toPath() },
            parameters.minSdkVersion.get(),
            KeepRulesConfig(emptyList(), emptyList()),
            false,
            OutputMode.ClassFile
        )
    }
}
