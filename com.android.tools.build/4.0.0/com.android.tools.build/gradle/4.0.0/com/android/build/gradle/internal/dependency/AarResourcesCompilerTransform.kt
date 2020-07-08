/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.options.SyncOptions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.xml.AndroidManifestParser
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

abstract class AarResourcesCompilerTransform :
    TransformAction<AarResourcesCompilerTransform.Parameters> {

    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val inputFile = primaryInput.get().asFile
        val manifest = inputFile.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML)
        val outputDir = transformOutputs.dir(getPackage(manifest.toPath()))
        outputDir.mkdirs()

        val resourceDir = File(inputFile, FD_RES)

        val resourceFolders = if (resourceDir.exists()) {
            resourceDir.listFiles { dir, name ->
                dir.isDirectory && !name.startsWith(FD_RES_VALUES)
            }
        } else {
            arrayOf<File>()
        }

        val requestList = ArrayList<CompileResourceRequest>()
        resourceFolders?.forEach { folder ->
            folder?.listFiles()?.forEach {
                // TODO(b/130160921): Add compile options
                requestList.add(CompileResourceRequest(it, outputDir))
            }
        }

        val aapt2ServiceKey =
            parameters.aapt2DaemonBuildService.get().registerAaptService(
                parameters.aapt2FromMaven.singleFile,
                LoggerWrapper.getLogger(this::class.java)
            )

        Aapt2CompileRunnable(
            Aapt2CompileRunnable.Params(
                aapt2ServiceKey,
                requestList,
                parameters.errorFormatMode.getOrElse(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            )
        ).run()
    }

    private fun getPackage(manifest: Path): String =
        BufferedInputStream(Files.newInputStream(manifest)).use {
            AndroidManifestParser.parse(it).`package`
        }

    interface Parameters : TransformParameters {
        @get:Input
        val aapt2Version: Property<String>
        @get:Internal
        val aapt2FromMaven: ConfigurableFileCollection
        @get:Internal
        val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
        @get:Internal
        val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>
    }
}