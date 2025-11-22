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

@file:JvmName("R8Tool")

package com.android.builder.dexing

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.PROGUARD_RULES_FOLDER
import com.android.SdkConstants.TOOLS_CONFIGURATION_FOLDER
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.builder.dexing.r8.R8DiagnosticsHandler
import com.android.ide.common.blame.MessageReceiver
import com.android.tools.r8.AndroidResourceInput
import com.android.tools.r8.AndroidResourceProvider
import com.android.tools.r8.ArchiveProgramResourceProvider
import com.android.tools.r8.ArchiveProtoAndroidResourceConsumer
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider
import com.android.tools.r8.AssertionsConfiguration
import com.android.tools.r8.BaseCompilerCommand
import com.android.tools.r8.ClassFileConsumer
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.DataDirectoryResource
import com.android.tools.r8.DataEntryResource
import com.android.tools.r8.DataResourceConsumer
import com.android.tools.r8.DataResourceProvider
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.ProgramResource
import com.android.tools.r8.ProgramResourceProvider
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.ResourcePath
import com.android.tools.r8.StringConsumer
import com.android.tools.r8.TextInputStream
import com.android.tools.r8.TextOutputStream
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.origin.PathOrigin
import com.android.tools.r8.profile.art.ArtProfileBuilder
import com.android.tools.r8.profile.art.ArtProfileConsumer
import com.android.tools.r8.profile.art.ArtProfileProvider
import com.android.tools.r8.references.Reference
import com.android.tools.r8.startup.StartupProfileBuilder
import com.android.tools.r8.startup.StartupProfileProvider
import com.android.tools.r8.utils.ArchiveResourceProvider
import com.google.common.io.ByteStreams
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

fun isProguardRule(name: String): Boolean {
    val lowerCaseName = name.toLowerCase(Locale.US)
    return lowerCaseName.startsWith("$PROGUARD_RULES_FOLDER/")
            || lowerCaseName.startsWith("/$PROGUARD_RULES_FOLDER/")
}

fun isToolsConfigurationFile(name: String): Boolean {
    val lowerCaseName = name.toLowerCase(Locale.US)
    return lowerCaseName.startsWith("$TOOLS_CONFIGURATION_FOLDER/")
            || lowerCaseName.startsWith("/$TOOLS_CONFIGURATION_FOLDER/")
}

sealed interface PartialShrinking: Serializable
data object PartialShrinkingIncludeAll : PartialShrinking {
    private fun readResolve(): Any = PartialShrinkingIncludeAll
}
data class PartialShrinkingConfig(
    val includedPatterns: List<String>
) : PartialShrinking { // Serializable so it can be used in Gradle workers
    companion object {

        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}

/**
 * Converts the specified inputs, according to the configuration, and writes dex or classes to
 * output path.
 */
fun runR8(
    inputClasses: Collection<Path>,
    output: Path,
    inputJavaResJar: Path,
    javaResourcesJar: Path,
    libraries: Collection<Path>,
    classpath: Collection<Path>,
    toolConfig: ToolConfig,
    proguardConfig: ProguardConfig,
    mainDexListConfig: MainDexListConfig,
    resourceShrinkingConfig: ResourceShrinkingConfig?,
    messageReceiver: MessageReceiver,
    featureClassJars: Collection<Path>,
    featureJavaResourceJars: Collection<Path>,
    featureDexDir: Path?,
    featureJavaResourceOutputDir: Path?,
    libConfiguration: String? = null,
    inputArtProfile: Path? = null,
    outputArtProfile: Path? = null,
    inputProfileForDexStartupOptimization: Path? = null,
    r8Metadata: Path? = null,
    partialShrinking: PartialShrinking? = null,
    r8ExecutorService: ExecutorService? = null // null only if called by tests
) {
    val logger: Logger = Logger.getLogger("R8")
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("*** Using R8 to process code ***")
        logger.fine("Tool config: $toolConfig")
        logger.fine("Proguard config: $proguardConfig")
        logger.fine("Main dex list config: $mainDexListConfig")
        logger.fine("Resource shrinking config: $resourceShrinkingConfig")
        logger.fine("Program classes: $inputClasses")
        logger.fine("Java resources: $inputJavaResJar")
        logger.fine("Library classes: $libraries")
        logger.fine("Classpath classes: $classpath")
        logger.fine("Partial shrinking includes: $partialShrinking")
    }
    val r8CommandBuilder =
        R8Command.builder(
            R8DiagnosticsHandler(
                proguardConfig.proguardOutputFiles.missingKeepRules,
                messageReceiver,
                toolConfig.mainDexListDisallowed,
                "R8"
            )
        )

    if (partialShrinking != null) {
        r8CommandBuilder.addPartialOptimizationConfigurationProviders(
            { builder ->
                when (partialShrinking) {
                    is PartialShrinkingIncludeAll -> builder.addPackageAndSubPackages(Reference.packageFromString(""))
                    is PartialShrinkingConfig -> {
                        partialShrinking.includedPatterns.forEach { include ->
                            if (include.isWildcard()) {
                                builder.addPackageAndSubPackages(
                                    Reference.packageFromString(
                                        include.cutWildcardSuffix()
                                    )
                                )
                            } else if (include.isPackage()) {
                                builder.addPackage(Reference.packageFromString(include.cutPackageSuffix()))
                            } else if (include.isClassName()) {
                                builder.addClass(Reference.classFromTypeName(include))
                            } else {
                                throw IllegalArgumentException(
                                    """
                            Partial shrinking includes can be:
                             - include packages that ends with '.*' like 'org.example.*'
                             - include packages and subpackages that ends with '.**' like 'org.example.*'
                             - include classes that ends with the first capital character of the last component
                               like 'org.example.MyClass'.
                             Still, you inclusion rule '${include}' does not belong to any category
                             """.trimIndent()
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    if (r8Metadata != null) {
        r8CommandBuilder.setBuildMetadataConsumer { metadata ->
            r8Metadata.writeText(metadata.toJson())
        }
    }

    if (toolConfig.r8OutputType == R8OutputType.DEX) {
        r8CommandBuilder.minApiLevel = toolConfig.minSdkVersion
        if (toolConfig.minSdkVersion < 21) {
            // specify main dex related options only when minSdkVersion is below 21
            r8CommandBuilder
                .addMainDexRulesFiles(mainDexListConfig.mainDexRulesFiles)
                .addMainDexListFiles(mainDexListConfig.mainDexListFiles)

            if (mainDexListConfig.mainDexRules.isNotEmpty()) {
                r8CommandBuilder.addMainDexRules(mainDexListConfig.mainDexRules, Origin.unknown())
            }
            mainDexListConfig.mainDexListOutput?.let {
                r8CommandBuilder.setMainDexListConsumer(StringConsumer.FileConsumer(it))
            }
        }
        if (libConfiguration != null) {
            r8CommandBuilder.addSpecialLibraryConfiguration(libConfiguration)
        }
        if (toolConfig.debuggable) {
            r8CommandBuilder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions
            )
        }
    }

    r8CommandBuilder
        .addProguardConfigurationFiles(
            proguardConfig.proguardConfigurationFiles.filter { Files.isRegularFile(it) }
        )
        .addProguardConfiguration(proguardConfig.proguardConfigurations, Origin.unknown())

    if (proguardConfig.proguardMapInput != null
        && Files.exists(proguardConfig.proguardMapInput)
    ) {
        r8CommandBuilder.addProguardConfiguration(
            listOf("-applymapping \"${proguardConfig.proguardMapInput}\""),
            Origin.unknown()
        )
    }

    val proguardOutputFiles = proguardConfig.proguardOutputFiles
    Files.deleteIfExists(proguardOutputFiles.proguardMapOutput)
    Files.deleteIfExists(proguardOutputFiles.proguardPartitionMapOutput)
    Files.deleteIfExists(proguardOutputFiles.proguardSeedsOutput)
    Files.deleteIfExists(proguardOutputFiles.proguardUsageOutput)
    Files.deleteIfExists(proguardOutputFiles.proguardConfigurationOutput)
    Files.deleteIfExists(proguardOutputFiles.missingKeepRules)

    Files.createDirectories(proguardOutputFiles.proguardMapOutput.parent)
    r8CommandBuilder.setProguardMapOutputPath(proguardOutputFiles.proguardMapOutput)
    r8CommandBuilder.setPartitionMapOutputPath(proguardOutputFiles.proguardPartitionMapOutput)
    r8CommandBuilder.setProguardSeedsConsumer(
        StringConsumer.FileConsumer(proguardOutputFiles.proguardSeedsOutput)
    )
    r8CommandBuilder.setProguardUsageConsumer(
        StringConsumer.FileConsumer(proguardOutputFiles.proguardUsageOutput)
    )
    r8CommandBuilder.setProguardConfigurationConsumer(
        StringConsumer.FileConsumer(
            proguardOutputFiles.proguardConfigurationOutput
        )
    )

    val dataResourceConsumer = JavaResourcesConsumer(javaResourcesJar)
    val programConsumer =
        if (toolConfig.r8OutputType == R8OutputType.CLASSES) {
            val baseConsumer: ClassFileConsumer = if (Files.isDirectory(output)) {
                ClassFileConsumer.DirectoryConsumer(output)
            } else {
                ClassFileConsumer.ArchiveConsumer(output)
            }
            object : ClassFileConsumer.ForwardingConsumer(baseConsumer) {
                override fun getDataResourceConsumer(): DataResourceConsumer? {
                    return dataResourceConsumer
                }
            }
        } else {
            val baseConsumer: DexIndexedConsumer = if (Files.isDirectory(output)) {
                DexIndexedConsumer.DirectoryConsumer(output)
            } else {
                DexIndexedConsumer.ArchiveConsumer(output)
            }
            object : DexIndexedConsumer.ForwardingConsumer(baseConsumer) {
                override fun getDataResourceConsumer(): DataResourceConsumer? {
                    return dataResourceConsumer
                }
            }
        }
    r8CommandBuilder.setProgramConsumer(programConsumer)

    r8CommandBuilder
        .setMode(if (toolConfig.debuggable) CompilationMode.DEBUG else CompilationMode.RELEASE)
        .setDisableTreeShaking(toolConfig.disableTreeShaking)
        .setDisableMinification(toolConfig.disableMinification)
        .setDisableDesugaring(toolConfig.disableDesugaring)
        .setProguardCompatibility(!toolConfig.fullMode)
        .enableLegacyFullModeForKeepRules(!toolConfig.strictFullModeForKeepRules)
        .apply { toolConfig.isolatedSplits?.let { setEnableIsolatedSplits(it) } }

    // Use this to control all resources provided to R8
    for (path in inputClasses) {
        when {
            Files.isRegularFile(path) -> r8CommandBuilder.addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(path)
            )

            Files.isDirectory(path) -> Files.walk(path).use { stream ->
                stream.filter {
                    val relativePath = path.relativize(it).toString()
                    Files.isRegularFile(it) && ClassFileInput.CLASS_MATCHER.test(relativePath)
                }
                    .forEach { r8CommandBuilder.addProgramFiles(it) }
            }

            else -> throw IOException("Unexpected file format: $path")
        }
    }

    r8CommandBuilder.addProgramResourceProvider(
        ResourceOnlyProvider(ArchiveResourceProvider.fromArchive(inputJavaResJar, true))
    )

    // handle art-profile rewriting if enabled
    inputArtProfile?.let {input ->
        if (input.exists() && outputArtProfile != null) {
            wireArtProfileRewriting(r8CommandBuilder, input, outputArtProfile)
        }
    }

    // Add startup profile if exists
    if (inputProfileForDexStartupOptimization != null &&
        inputProfileForDexStartupOptimization.toFile().exists()) {
        wireMinimalStartupOptimization(r8CommandBuilder, inputProfileForDexStartupOptimization)
    }

    // Enable workarounds for missing library APIs in R8 (see b/231547906).
    r8CommandBuilder.setEnableExperimentalMissingLibraryApiModeling(true);

    resourceShrinkingConfig?.let { setupResourceShrinking(r8CommandBuilder, it) }

    setupFeatureSplits(
        r8CommandBuilder, toolConfig, featureClassJars, featureDexDir,
        featureJavaResourceJars, featureJavaResourceOutputDir, resourceShrinkingConfig
    )

    ClassFileProviderFactory(libraries).use { libraryClasses ->
        ClassFileProviderFactory(classpath).use { classpathClasses ->
            r8CommandBuilder.addLibraryResourceProvider(libraryClasses.orderedProvider)
            r8CommandBuilder.addClasspathResourceProvider(classpathClasses.orderedProvider)
            val r8Command = r8CommandBuilder.build()
            r8ExecutorService?.let {
                R8.run(r8Command, it)
            } ?: run {
                R8.run(r8Command)
            }
        }
    }

    proguardConfig.proguardOutputFiles.proguardMapOutput.let {
        if (Files.notExists(it)) {
            // R8 might not create a mapping file, so we have to create it, http://b/37053758.
            Files.createFile(it)
        }
    }
}

private fun String.isClassName(): Boolean {
    if(isEmpty()) return false
    val lastDotIndex = lastIndexOf('.')
    if (lastDotIndex == -1) {
        // maybe class with no package
        return this[0].isUpperCase()
    }
    val charAfterDotIndex = lastDotIndex + 1
    return if (charAfterDotIndex < length) {
        this[charAfterDotIndex].isUpperCase()
    } else {
        false
    }
}

private const val wildcardSuffix = ".**"
private fun String.isWildcard(): Boolean = this.endsWith(wildcardSuffix)
private fun String.cutWildcardSuffix(): String = removeSuffix(wildcardSuffix)

private const val packageSuffix = ".*"
private fun String.isPackage(): Boolean = this.endsWith(packageSuffix)
private fun String.cutPackageSuffix(): String = removeSuffix(packageSuffix)

private fun setupResourceShrinking(
    r8CommandBuilder: R8Command.Builder,
    config: ResourceShrinkingConfig
) {
    // Use R8's FeatureSplit API to handle both multi-APKs (with multiple input files) and a regular
    // APK (with only 1 input file).
    config.linkedResourcesInputFiles.forEachIndexed { index, linkedResourcesInputFile ->
        r8CommandBuilder.addFeatureSplit {
            it.setAndroidResourceProvider(
                ArchiveProtoAndroidResourceProvider(linkedResourcesInputFile.toPath()))
            it.setAndroidResourceConsumer(
                ArchiveProtoAndroidResourceConsumer(config.shrunkResourcesOutputFiles[index].toPath()))

            // R8 requires a program consumer to be set
            it.setProgramConsumer(DexIndexedConsumer.emptyConsumer())

            it.build()
        }
    }

    r8CommandBuilder.setAndroidResourceProvider(
        KeepRulesAndroidResourceProvider(config.mergedNotCompiledResourcesInputDirs))
    // R8 requires an Android resource consumer to be set
    r8CommandBuilder.setAndroidResourceConsumer { _, _ ->  }

    r8CommandBuilder.setResourceShrinkerConfiguration {
        if (config.optimizedShrinking) {
            it.enableOptimizedShrinkingWithR8()
        }
        config.logFile?.let { logFile ->
            it.setDebugConsumer(StringConsumer.FileConsumer(logFile.toPath()))
        }
        it.build()
    }
}

/** [AndroidResourceProvider] that provides [KeepRulesAndroidResourceInput]. */
private class KeepRulesAndroidResourceProvider(
    private val mergedNotCompiledResourcesInputDirs: List<File>
) : AndroidResourceProvider {

    override fun getAndroidResources(): List<KeepRulesAndroidResourceInput> {
        return mergedNotCompiledResourcesInputDirs.flatMap {
            it.walk()
                .filter { it.path.endsWith(DOT_XML) }
                .map { KeepRulesAndroidResourceInput(it) }
                .toList()
        }
    }
}

/**
 * [AndroidResourceInput] where
 * [AndroidResourceInput.Kind] == [AndroidResourceInput.Kind.KEEP_RULE_FILE].
 */
private class KeepRulesAndroidResourceInput(
    /** XML file which contains keep rules for resources. */
    private val resourceKeepRulesXmlFile: File
) : AndroidResourceInput {

    override fun getOrigin() = PathOrigin(resourceKeepRulesXmlFile.toPath())

    override fun getPath() = ResourcePath {
        // R8 expects this path to be in Unix style
        resourceKeepRulesXmlFile.invariantSeparatorsPath
    }

    override fun getKind() = AndroidResourceInput.Kind.KEEP_RULE_FILE

    override fun getByteStream() = ByteArrayInputStream(resourceKeepRulesXmlFile.readBytes())
}

private fun setupFeatureSplits(
    r8CommandBuilder: R8Command.Builder,
    toolConfig: ToolConfig,
    featureClassJars: Collection<Path>,
    featureDexOutputDir: Path?,
    featureJavaResourceJars: Collection<Path>,
    featureJavaResourceOutputDir: Path?,
    resourceShrinkingConfig: ResourceShrinkingConfig?,
) {
    if (featureClassJars.isEmpty()) return

    check(toolConfig.r8OutputType == R8OutputType.DEX) {
        "Unexpected toolConfig.r8OutputType: ${toolConfig.r8OutputType}"
    }
    val featureFileNamesWithoutExtension = featureClassJars.map { it.toFile().nameWithoutExtension }.distinct()
    check(featureJavaResourceJars.map { it.toFile().nameWithoutExtension } == featureFileNamesWithoutExtension
            && (resourceShrinkingConfig == null || resourceShrinkingConfig.featureLinkedResourcesInputFiles.map { it.nameWithoutExtension } == featureFileNamesWithoutExtension)) {
        "Inconsistent featureFileNames:\n" +
                "  featureClassJars: $featureClassJars\n" +
                "  featureJavaResourceJars: $featureJavaResourceJars\n" +
                "  featureLinkedResourcesInputFiles: ${resourceShrinkingConfig?.featureLinkedResourcesInputFiles}"
    }
    check(
        featureDexOutputDir != null
                && featureJavaResourceOutputDir != null
                && (resourceShrinkingConfig == null || resourceShrinkingConfig.featureShrunkResourcesOutputDir != null)
    ) {
        "Expected not null but received:\n" +
                "  featureDexDir=$featureDexOutputDir\n" +
                "  featureJavaResourceOutputDir=$featureJavaResourceOutputDir\n" +
                "  featureShrunkResourcesOutputDir=${resourceShrinkingConfig?.featureShrunkResourcesOutputDir}"
    }

    featureFileNamesWithoutExtension.forEach { featureFileNameWithoutExtension ->
        val featureClassJar = featureClassJars.single { it.nameWithoutExtension == featureFileNameWithoutExtension }
        val featureJavaResourceJar = featureJavaResourceJars.single { it.nameWithoutExtension == featureFileNameWithoutExtension }

        val featureDexOutputDirectory = Files.createDirectories(featureDexOutputDir.resolve(featureFileNameWithoutExtension))
        val featureJavaResourceOutputDirectory = featureJavaResourceOutputDir.resolve("$featureFileNameWithoutExtension$DOT_JAR")

        r8CommandBuilder.addFeatureSplit {
            it.addProgramResourceProvider(ArchiveProgramResourceProvider.fromArchive(featureClassJar))
            it.addProgramResourceProvider(ArchiveResourceProvider.fromArchive(featureJavaResourceJar, true))

            val javaResourcesConsumer = JavaResourcesConsumer(featureJavaResourceOutputDirectory)
            it.setProgramConsumer(
                object : DexIndexedConsumer.DirectoryConsumer(featureDexOutputDirectory) {
                    override fun getDataResourceConsumer(): DataResourceConsumer {
                        return javaResourcesConsumer
                    }
                }
            )

            if (resourceShrinkingConfig != null) {
                val inputFile = resourceShrinkingConfig.featureLinkedResourcesInputFiles
                    .single { file -> file.nameWithoutExtension == featureFileNameWithoutExtension }
                val outputFile = resourceShrinkingConfig.featureShrunkResourcesOutputDir!!.resolve(inputFile.name)
                it.setAndroidResourceProvider(ArchiveProtoAndroidResourceProvider(inputFile.toPath()))
                it.setAndroidResourceConsumer(ArchiveProtoAndroidResourceConsumer(outputFile.toPath()))
            }

            it.build()
        }
    }
}

fun wireArtProfileRewriting(
    commandBuilder: BaseCompilerCommand.Builder<*, *>,
    inputArtProfile: Path,
    outputArtProfile: Path
) {
    // Supply the ART profile to the compiler by supplying an instance of ArtProfileProvider.
    val artProfileProvider: ArtProfileProvider = object : ArtProfileProvider {
        override fun getArtProfile(profileBuilder: ArtProfileBuilder) {
            profileBuilder.addHumanReadableArtProfile(
                object : TextInputStream {
                    override fun getInputStream(): InputStream =
                        Files.newInputStream(inputArtProfile)

                    override fun getCharset(): Charset = StandardCharsets.UTF_8
                }
            ) {}
        }

        override fun getOrigin(): Origin {
            return PathOrigin(inputArtProfile)
        }
    }

    // Create a consumer for retrieving the residual ART profile from the compiler.
    val residualArtProfileConsumer: ArtProfileConsumer = object : ArtProfileConsumer {
        override fun getHumanReadableArtProfileConsumer(): TextOutputStream {
            return object : TextOutputStream {
                override fun getOutputStream(): OutputStream =
                    Files.newOutputStream(outputArtProfile)

                override fun getCharset(): Charset = StandardCharsets.UTF_8
            }
        }

        override fun finished(handler: DiagnosticsHandler) {}
    }

    commandBuilder.addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer)
}

private fun wireMinimalStartupOptimization(
    r8CommandBuilder: R8Command.Builder,
    inputArtProfile: Path,
) {
    r8CommandBuilder.addStartupProfileProviders(object : StartupProfileProvider {
        override fun getOrigin(): Origin {
            return PathOrigin(inputArtProfile)
        }

        override fun getStartupProfile(p0: StartupProfileBuilder) {
            p0.addHumanReadableArtProfile(
                object : TextInputStream {
                    override fun getInputStream(): InputStream =
                        Files.newInputStream(inputArtProfile)

                    override fun getCharset(): Charset = StandardCharsets.UTF_8
                }) {}
        }
    })
}

enum class R8OutputType {
    DEX,
    CLASSES,
}

/** Main dex related parameters for the R8 tool. */
data class MainDexListConfig(
    val mainDexRulesFiles: Collection<Path> = listOf(),
    val mainDexListFiles: Collection<Path> = listOf(),
    val mainDexRules: List<String> = listOf(),
    val mainDexListOutput: Path? = null
)

/** Proguard-related parameters for the R8 tool. */
data class ProguardConfig(
    val proguardConfigurationFiles: List<Path>,
    val proguardMapInput: Path?,
    val proguardConfigurations: List<String>,
    val proguardOutputFiles: ProguardOutputFiles
)

data class ProguardOutputFiles(
    val proguardMapOutput: Path,
    val proguardPartitionMapOutput: Path,
    val proguardSeedsOutput: Path,
    val proguardUsageOutput: Path,
    val proguardConfigurationOutput: Path,
    val missingKeepRules: Path
)

/** Basic parameters required for running R8. */
data class ToolConfig(
    val minSdkVersion: Int,
    val debuggable: Boolean,
    val disableTreeShaking: Boolean,
    val disableMinification: Boolean,
    val disableDesugaring: Boolean,
    val fullMode: Boolean,
    val strictFullModeForKeepRules: Boolean,
    val isolatedSplits: Boolean?,
    val r8OutputType: R8OutputType,
    val mainDexListDisallowed: Boolean
) : Serializable { // Serializable so it can be used in Gradle workers

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 0L
    }
}

/** Parameters required for running resource shrinking. */
data class ResourceShrinkingConfig(
    val linkedResourcesInputFiles: List<File>,
    val mergedNotCompiledResourcesInputDirs: List<File>,
    val featureLinkedResourcesInputFiles: List<File>,
    val optimizedShrinking: Boolean,
    val nonFinalResIds: Boolean,
    val logFile: File?,
    val shrunkResourcesOutputFiles: List<File>,
    val featureShrunkResourcesOutputDir: File?
) : Serializable { // Serializable so it can be used in Gradle workers

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 0L
    }
}

private class ProGuardRulesFilteringVisitor(
    private val visitor: DataResourceProvider.Visitor?
) : DataResourceProvider.Visitor {

    override fun visit(directory: DataDirectoryResource) {
        visitor?.visit(directory)
    }

    override fun visit(resource: DataEntryResource) {
        if (!isProguardRule(resource.getName()) && !isToolsConfigurationFile(resource.getName())) {
            visitor?.visit(resource)
        }
    }
}

private class ResourceOnlyProvider(val originalProvider: ArchiveResourceProvider) :
    ProgramResourceProvider {

    override fun getProgramResources() = listOf<ProgramResource>()

    override fun getProgramResources(consumer: Consumer<ProgramResource>?) {}

    override fun getDataResourceProvider() = object : DataResourceProvider {
        override fun accept(visitor: DataResourceProvider.Visitor?) {
            val visitorWrapper = ProGuardRulesFilteringVisitor(visitor)
            originalProvider.accept(visitorWrapper);
        }
    }
}

/** Custom Java resources consumer to make sure we compress Java resources in the jar. */
private class JavaResourcesConsumer(private val outputJar: Path) : DataResourceConsumer {

    private val output =
        lazy { ZipOutputStream(BufferedOutputStream(outputJar.toFile().outputStream())) }
    private val zipLock = Any()

    /** Accept can be called from multiple threads. */
    override fun accept(directory: DataDirectoryResource, diagnosticsHandler: DiagnosticsHandler) {
        val entry: ZipEntry = createNewZipEntry(directory.getName() + "/")
        synchronized(zipLock) {
            output.value.putNextEntry(entry)
            output.value.closeEntry()
        }
    }

    /** Accept can be called from multiple threads. */
    override fun accept(file: DataEntryResource, diagnosticsHandler: DiagnosticsHandler) {
        val entry: ZipEntry = createNewZipEntry(file.getName())
        synchronized(zipLock) {
            output.value.putNextEntry(entry)
            output.value.write(ByteStreams.toByteArray(file.getByteStream()))
            output.value.closeEntry()
        }
    }

    override fun finished(handler: DiagnosticsHandler) {
        output.value.close()
    }

    private fun createNewZipEntry(name: String): ZipEntry {
        return ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            time = 0
        }
    }
}
