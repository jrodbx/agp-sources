/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Transform core-for-system-modules.jar into a JDK image used for compiling Java 9+ source
 */
@CacheableTransform
abstract class JdkImageTransform : TransformAction<JdkImageTransform.Parameters> {

    interface Parameters : GenericTransformParameters {
        @get:Input
        val jdkId: Property<String>

        @get:Internal
        val javaHome: Property<File>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputDir = outputs.dir("output")
        // This is a temporary directory containing intermediate outputs, which must be cleaned up after the transform.
        val tempDir = outputDir.resolve("temp")
        val jdkImageDir = outputDir.resolve(JDK_IMAGE_OUTPUT_DIR)

        JdkImageTransformDelegate(
            inputArtifact.get().asFile,
            tempDir,
            jdkImageDir,
            JdkTools(
                parameters.javaHome.get(),
                GradleProcessExecutor(execOperations::exec),
                LoggerWrapper.getLogger(this::class.java)
            )
        ).run()

        // Make sure the temporary directory is deleted
        FileUtils.deleteRecursivelyIfExists(tempDir)
    }

}

const val ANDROID_JDK_IMAGE = "_internal_android_jdk_image"
const val CONFIG_NAME_ANDROID_JDK_IMAGE = "androidJdkImage"
const val JDK_IMAGE_OUTPUT_DIR = "jdkImage"
private val ATTR_JDK_ID: Attribute<String> = Attribute.of("jdk-id", String::class.java)

fun getJdkImageFromTransform(project: Project): FileCollection {
    val configuration = project.configurations.getByName(CONFIG_NAME_ANDROID_JDK_IMAGE)
    return configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                ANDROID_JDK_IMAGE
            )
            it.attribute(ATTR_JDK_ID, getJdkId(project))
        }
    }.artifacts.artifactFiles
}

fun getJavaHome(project: Project): Provider<File> {
    return project.providers.systemProperty("java.home").flatMap {
        project.provider { File(it) }
    }
}

fun getJdkId(project: Project): String {
    return project.providers.systemProperty("java.version").forUseAtConfigurationTime().get() +
        project.providers.systemProperty("java.vendor").forUseAtConfigurationTime().get()
}
