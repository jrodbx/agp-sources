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

import com.android.SdkConstants
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.tools.ToolProvider
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

/*
* Invokes the specified library intended to generate client SDK shim code for interacting with a
* privacy-sandbox-sdk. The SDK shim code generator library will receive the classes from the
* sdk-interface-descriptors.jar from an ASAR as input.
*/
@DisableCachingByDefault
abstract class ExtractSdkShimTransform : TransformAction<ExtractSdkShimTransform.Parameters> {

    interface Parameters: GenericTransformParameters {

        // This is temporary until permanent method of getting apigenerator dependencies is finished.
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val apiGenerator: ConfigurableFileCollection

        @get:Nested
        val buildTools: BuildToolsExecutableInput

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val kotlinCompiler: ConfigurableFileCollection

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val bootstrapClasspath: ConfigurableFileCollection

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val runtimeDependencies: ConfigurableFileCollection

        @get:Input
        val requireServices: Property<Boolean>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val sdkInterfaceDescriptorJar = inputArtifact.get().asFile
        if (!sdkInterfaceDescriptorJar.isFile) {
            throw IOException("${sdkInterfaceDescriptorJar.absolutePath} must be a file.")
        }
        if (checkEmptyJar(sdkInterfaceDescriptorJar)) {
            if (parameters.requireServices.get()) {
                throw RuntimeException(
                        "Unable to proceed generating shim with no provided sdk descriptor entries in: " +
                                "${sdkInterfaceDescriptorJar.absolutePath}." +
                                "Privacy Sandbox Sdk modules require at least one service declaration.")
            }
            return
        }
        val output = transformOutputs.file("sdk-shim-generated.jar")
        val aidlExecutable = parameters.buildTools.aidlExecutableProvider().get().absoluteFile
        val totalClasspath =
                parameters.bootstrapClasspath.files + parameters.runtimeDependencies.files
        val totalClasspathStr = totalClasspath.joinToString(File.pathSeparator) { it.path }
        val apiGeneratorJarsFiles = parameters.apiGenerator.files
                ?: error("No library has been specified for client SDK shim generation.")
        val apiGeneratorUrls: Array<URL> =
                apiGeneratorJarsFiles.mapNotNull { it.toURI().toURL() }.toTypedArray()
        val tempDirForApiGeneratorOutputs = Files.createTempDirectory("extract-shim-transform")
        try {
            val kotlinOutDir = File(tempDirForApiGeneratorOutputs.toFile(), "kotlin-compiled")
            val javaOutDir = File(tempDirForApiGeneratorOutputs.toFile(), "java-compiled")

            URLClassLoader(apiGeneratorUrls).use {
                // sandbox-apigenerator is invoked by reflection to avoid handling versioning.
                Generator(it).generate(
                        sdkInterfaceDescriptors = sdkInterfaceDescriptorJar.toPath(),
                        aidlCompiler = aidlExecutable.toPath(),
                        outputDirectory = tempDirForApiGeneratorOutputs)
            }

            val generatedFiles: List<Path> = Files.walk(tempDirForApiGeneratorOutputs).use { stream ->
                 stream.filter { Files.isRegularFile(it) }.collect(Collectors.toList())
            }

            // Java sources are produced by the apigenerator invoking the aidl compiler, therefore
            // java sources do not reference Kotlin sources.
            compileKotlin(generatedFiles, totalClasspathStr, kotlinOutDir)
            compileJava(generatedFiles, totalClasspathStr, javaOutDir)

            ZipOutputStream(BufferedOutputStream(output.outputStream())).use { outJar ->
                writeCompiledClassesToZip(kotlinOutDir, outJar)
                writeCompiledClassesToZip(javaOutDir, outJar)
            }
        }
        finally {
            try {
                FileUtils.deleteRecursivelyIfExists(tempDirForApiGeneratorOutputs.toFile())
            } catch (e: AccessDeniedException) {
                Logger.getLogger(ExtractCompileSdkShimTransform::class.java.name)
                        .log(Level.WARNING, e.message)
            }
        }
    }

    private fun checkEmptyJar(sdkInterfaceDescriptorJar: File): Boolean {
        ZipInputStream(sdkInterfaceDescriptorJar?.inputStream()).use { jar ->
            return jar.nextEntry == null
        }
    }

    private fun compileJava(generatedFiles: List<Path>, totalClasspathStr: String, javaOutDir: File) {
        val javac = ToolProvider.getSystemJavaCompiler()
        val manager = javac.getStandardFileManager(null, null, null)
        val javaSourceClasspaths =
                generatedFiles.filter { it.extension == SdkConstants.EXT_JAVA }
        val source =
                manager.getJavaFileObjectsFromFiles(javaSourceClasspaths.map { it.toFile() })
        javac.getTask(null,
                manager, null,
                ImmutableList.of(
                        "-classpath", totalClasspathStr,
                        "-d", javaOutDir.absolutePath), null,
                source
        ).call()
    }

    private fun compileKotlin(generatedFiles: List<Path>, totalClasspathStr: String, kotlinOutDir: File) {
        execOperations.javaexec { spec ->
            spec.mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompilerKt")
            spec.classpath(parameters.kotlinCompiler)
            spec.args = listOf(
                    "-no-jdk",
                    "-no-reflect") + generatedFiles.map { it.pathString } + listOf(
                    "-classpath", totalClasspathStr,
                    "-d", kotlinOutDir.path)
        }
    }

    /* For invoking the sandbox-apigenerator via reflection. */
    class Generator(val classLoader: URLClassLoader) {
        private val apiGeneratorPackage = "androidx.privacysandbox.tools.apigenerator"
        private val privacySandboxApiGeneratorClass =
                classLoader.loadClass("$apiGeneratorPackage.PrivacySandboxApiGenerator")
        private val privacySandboxSdkGenerator = privacySandboxApiGeneratorClass
                .getConstructor()
                .newInstance()
        private val generateMethod = privacySandboxApiGeneratorClass
                .getMethod("generate", Path::class.java, Path::class.java, Path::class.java)
        fun generate(
                sdkInterfaceDescriptors: Path,
                aidlCompiler: Path,
                outputDirectory: Path) {
            generateMethod.invoke(privacySandboxSdkGenerator,
                            sdkInterfaceDescriptors, aidlCompiler, outputDirectory)
        }
    }

    private fun writeCompiledClassesToZip(classesDir: File, outJar: ZipOutputStream) {
        Files.walk(classesDir.toPath()).filter { Files.isRegularFile(it) }.forEach {
            val entry = ZipEntry(it.toFile().relativeTo(classesDir).invariantSeparatorsPath)
            outJar.putNextEntry(entry)
            it.inputStream().use { inputStream ->
                outJar.write(inputStream.readBytes())
            }
            outJar.closeEntry()
        }
    }
}

@DisableCachingByDefault
abstract class ExtractCompileSdkShimTransform : ExtractSdkShimTransform()

@DisableCachingByDefault
abstract class ExtractRuntimeSdkShimTransform : ExtractSdkShimTransform()
