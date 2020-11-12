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

package com.android.build.gradle.internal.lint

import com.android.Version
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.ApiVersion
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.model.DefaultLintModelAndroidArtifact
import com.android.tools.lint.model.DefaultLintModelBuildFeatures
import com.android.tools.lint.model.DefaultLintModelDependencies
import com.android.tools.lint.model.DefaultLintModelDependencyGraph
import com.android.tools.lint.model.DefaultLintModelJavaArtifact
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.DefaultLintModelLintOptions
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModule
import com.android.tools.lint.model.DefaultLintModelResourceField
import com.android.tools.lint.model.DefaultLintModelVariant
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelFactory
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibraryResolver
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelSerialization
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import javax.inject.Inject

/**
 * Task to write the [LintModelModule] representation of the Gradle project on disk.
 *
 * This serialized [LintModelModule] file is then consumed by Lint in consuming projects to get all the
 * information about this project.
 */
abstract class LintModelModuleWriterTask : NonIncrementalGlobalTask() {

    // this is an input and not an inputDirectory because we don't care about the content, just the
    // location
    @get:Input
    abstract val projectDirectory: Property<File>

    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val moduleType: Property<LintModelModuleType>

    @get:Input
    abstract val groupId: Property<String>
    @get:Input
    abstract val artifactId: Property<String>

    // this is an input and not an inputDirectory because we don't care about the content, just the
    // location
    @get:Input
    abstract val buildDirectory: Property<File>

    @get:Input
    abstract val lintOptions: Property<DefaultLintModelLintOptions>

    @get:Input
    abstract val viewBinding: Property<Boolean>

    @get:Input
    abstract val coreLibraryDesugaringEnabled: Property<Boolean>

    @get:Input
    abstract val namespacingMode: Property<LintModelNamespacingMode>

    @get:Input
    @get:Optional
    abstract val resourcePrefix: Property<String>

    @get:Input
    abstract val dynamicFeatures: ListProperty<String>

    @get:Input
    abstract val bootClasspath: ListProperty<File>

    @get:Input
    abstract val javaSourceLevel: Property<JavaVersion>

    @get:Input
    abstract val compileTarget: Property<String>

    @get:Input
    abstract val neverShrinking: Property<Boolean>

    @get:Nested
    abstract val variantInputs: ListProperty<VariantInput>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val module = DefaultLintModelModule(
            loader = null,
            dir = projectDirectory.get(),
            modulePath = modulePath.get(),
            type = moduleType.get(),
            mavenName = DefaultLintModelMavenName(groupId.get(), artifactId.get()),
            gradleVersion = GradleVersion.tryParse(Version.ANDROID_GRADLE_PLUGIN_VERSION),
            buildFolder = buildDirectory.get(),
            lintOptions = lintOptions.get(),
            lintRuleJars = listOf(),
            resourcePrefix = resourcePrefix.orNull,
            dynamicFeatures = dynamicFeatures.get(),
            bootClassPath = bootClasspath.get(),
            javaSourceLevel = javaSourceLevel.get().toString(),
            compileTarget = compileTarget.get(),
            variants = listOf(),
            neverShrinking = neverShrinking.get(),
            oldProject = null // oldProject
        )

        val viewBindingValue = viewBinding.get()
        val coreLibraryDesugaringEnabledValue = coreLibraryDesugaringEnabled.get()
        val namespacingModeValue = namespacingMode.get()

        LintModelSerialization.writeModule(
            module = module,
            destination = outputDirectory.get().asFile,
            writeVariants = variantInputs.get().map {
                it.convertToLintModelVariant(
                    module,
                    viewBindingValue,
                    coreLibraryDesugaringEnabledValue,
                    namespacingModeValue
                )
            },
            writeDependencies = false
        )
    }

    /**
     * Inputs for a given variants. The task has a list of these with a [Nested] annotation
     */
    abstract class VariantInput @Inject constructor(
        @get:Input
        val name: String,
        @get:Input
        val minifiedEnabled: Boolean
    ) {
        @get:Nested
        abstract val mainArtifact: Property<AndroidArtifactInput>

        @get:Nested
        @get:Optional
        abstract val testArtifact: Property<JavaArtifactInput>

        @get:Nested
        @get:Optional
        abstract val androidTestArtifact: Property<AndroidArtifactInput>

        @get:Input
        abstract val mergedManifest: Property<File>

        @get:Input
        abstract val manifestMergeReport: Property<File>

        @get:Input
        abstract val packageName: Property<String>

        @get:Nested
        abstract val minSdkVersion: Property<SdkVersion>

        @get:Nested
        abstract val targetSdkVersion: Property<SdkVersion>

        @get:Input
        abstract val resValues: ListProperty<DefaultLintModelResourceField>

        @get:Input
        abstract val manifestPlaceholders: MapProperty<String, String>

        @get:Input
        abstract val resourceConfigurations: ListProperty<String>

        @get:Input
        abstract val proguardFiles: ListProperty<File>

        @get:Input
        abstract val consumerProguardFiles: ListProperty<File>

        @get:Input
        abstract val sourceProviders: ListProperty<LintModelSourceProvider>

        @get:Input
        abstract val testSourceProviders: ListProperty<LintModelSourceProvider>

        @get:Input
        abstract val debuggable: Property<Boolean>


        fun convertToLintModelVariant(
            module: LintModelModule,
            viewBinding: Boolean,
            coreLibraryDesugaringEnabled: Boolean,
            namespacingMode: LintModelNamespacingMode
        ): LintModelVariant {
            // empty resolver since we are not dealing with dependencies in this task
            val libraryResolver = DefaultLintModelLibraryResolver(mapOf())

            return DefaultLintModelVariant(
                module,
                name,
                useSupportLibraryVectorDrawables = false,
                mainArtifact = mainArtifact.get().convert(libraryResolver),
                testArtifact = testArtifact.orNull?.convert(libraryResolver),
                androidTestArtifact = androidTestArtifact.orNull?.convert(libraryResolver),
                mergedManifest = mergedManifest.get(),
                manifestMergeReport = manifestMergeReport.get(),
                `package` = packageName.get(),
                minSdkVersion = minSdkVersion.get().convert(),
                targetSdkVersion = targetSdkVersion.get().convert(),
                resValues = resValues.get().associateBy { it.name },
                manifestPlaceholders = manifestPlaceholders.get(),
                resourceConfigurations = resourceConfigurations.get(),
                proguardFiles = proguardFiles.get(),
                consumerProguardFiles = consumerProguardFiles.get(),
                sourceProviders = listOf(), //FIXME
                testSourceProviders = listOf(), //FIXME
                debuggable = false, //FIXME
                shrinkable = false, //FIXME
                buildFeatures = DefaultLintModelBuildFeatures(
                    viewBinding,
                    coreLibraryDesugaringEnabled,
                    namespacingMode
                ),
                libraryResolver = libraryResolver,
                oldVariant = null
            )
        }

        private fun SdkVersion.convert(): AndroidVersion =
            AndroidVersion(apiLevel.get(), codeName.orNull)

        private fun AndroidArtifactInput.convert(
            libraryResolver: LintModelLibraryResolver
        ): LintModelAndroidArtifact {
            val classFolders = mutableListOf<File>()
            classFolders.add(javacOutFolder.get())
            classFolders.addAll(additionalClasses.files)

            return DefaultLintModelAndroidArtifact(
                applicationId.get(),
                generatedResourceFolders.get(),
                generatedSourceFolders.get(),
                classFolders,
                getEmptyDependencies(libraryResolver)
            )
        }

        private fun JavaArtifactInput.convert(
            libraryResolver: LintModelLibraryResolver
        ): LintModelJavaArtifact {
            val classFolders = mutableListOf<File>()
            classFolders.add(javacOutFolder.get())
            classFolders.addAll(additionalClasses.files)

            return DefaultLintModelJavaArtifact(
                classFolders,
                getEmptyDependencies(libraryResolver)
            )
        }

        private fun getEmptyDependencies(
            libraryResolver: LintModelLibraryResolver
        ): LintModelDependencies =
            DefaultLintModelDependencies(
                DefaultLintModelDependencyGraph(listOf(), libraryResolver),
                DefaultLintModelDependencyGraph(listOf(), libraryResolver),
                libraryResolver
            )
    }

    /**
     * Inputs for an SdkVersion. This is used by [VariantInput] for min/target SDK Version
     */
    abstract class SdkVersion {
        @get:Input
        abstract val apiLevel: Property<Int>

        @get:Input
        @get:Optional
        abstract val codeName: Property<String?>
    }

    /**
     * Inputs for an Android Artifact. This is used by [VariantInput] for the main and AndroidTest
     * artifacts.
     */
    abstract class AndroidArtifactInput: ArtifactInput() {
        @get:Input
        abstract val applicationId: Property<String>

        @get:Input
        abstract val generatedSourceFolders: ListProperty<File>

        @get:Input
        abstract val generatedResourceFolders: ListProperty<File>
    }

    /**
     * Inputs for a Java Artifact. This is used by [VariantInput] for the unit test artifact.
     */
    abstract class JavaArtifactInput : ArtifactInput()

    /**
     * Base Inputs for Android/Java artifacts
     */
    abstract class ArtifactInput {
        @get:Input
        abstract val javacOutFolder: Property<File>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        abstract val additionalClasses: ConfigurableFileCollection
    }

    class CreationAction(
        globalScope: GlobalScope,
        private val variantPropertiesList: List<VariantImpl>,
        private val testPropertiesList: List<TestComponentImpl>,
        private val variantType: VariantType,
        private val buildFeatures: BuildFeatureValues
    ) : GlobalTaskCreationAction<LintModelModuleWriterTask>(globalScope) {

        override val name: String
            get() = "generateLintModuleInfo"
        override val type: Class<LintModelModuleWriterTask>
            get() = LintModelModuleWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelModuleWriterTask>) {
            super.handleProvider(taskProvider)

            globalScope.globalArtifacts
                .setInitialProvider(
                    taskProvider,
                    LintModelModuleWriterTask::outputDirectory
                )
                .on(InternalArtifactType.LINT_PROJECT_GLOBAL_MODEL)
        }

        override fun configure(task: LintModelModuleWriterTask) {
            super.configure(task)

            val project = task.project
            val extension = globalScope.extension

            task.projectDirectory.setDisallowChanges(project.projectDir)
            task.modulePath.setDisallowChanges(project.path)
            task.moduleType.setDisallowChanges(variantType.toLintModelModuleType())
            task.groupId.setDisallowChanges(project.group.toString())
            task.artifactId.setDisallowChanges(project.name)
            task.buildDirectory.setDisallowChanges(project.layout.buildDirectory.map { it.asFile })
            task.lintOptions.setDisallowChanges(LintModelFactory.getLintOptions(extension.lintOptions))
            task.viewBinding.setDisallowChanges(buildFeatures.viewBinding)
            task.coreLibraryDesugaringEnabled.setDisallowChanges(false) //FIXME
            task.namespacingMode.setDisallowChanges(LintModelNamespacingMode.DISABLED) // FIXME
            task.resourcePrefix.setDisallowChanges(extension.resourcePrefix)

            if (extension is BaseAppModuleExtension) {
              task.dynamicFeatures.setDisallowChanges(extension.dynamicFeatures)
            }

            task.bootClasspath.setDisallowChanges(globalScope.bootClasspath.get().map { it.asFile })
            task.javaSourceLevel.setDisallowChanges(globalScope.extension.compileOptions.sourceCompatibility)
            task.compileTarget.setDisallowChanges(globalScope.extension.compileSdkVersion)
            task.neverShrinking.setDisallowChanges(globalScope.extension.buildTypes.none { it.isMinifyEnabled  })

            configureVariants(task)
        }

        private fun configureVariants(task: LintModelModuleWriterTask) {
            for (variantProperties in variantPropertiesList) {
                task.variantInputs.add(createVariantInput(variantProperties))
            }
            task.variantInputs.disallowChanges()
        }

        private fun createVariantInput(
            variant: VariantImpl
        ): VariantInput {
            return globalScope.dslServices.newInstance(
                VariantInput::class.java,
                variant.name,
                if (variant is ApkCreationConfig) variant.minifiedEnabled else false
            ).also { variantInput ->
                variantInput.mainArtifact.setDisallowChanges(getAndroidArtifact(variant))

                getTestComponent(variant, UnitTestImpl::class.java)?.let {
                    variantInput.testArtifact.setDisallowChanges(getJavaArtifact(it))
                }

                getTestComponent(variant, AndroidTestImpl::class.java)?.let {
                    variantInput.androidTestArtifact.setDisallowChanges(getAndroidArtifact(it))
                }

                variantInput.packageName.setDisallowChanges(variant.packageName)

                variantInput.minSdkVersion.setDisallowChanges(variant.variantBuilder.minSdkVersion.convert())
                variantInput.targetSdkVersion.setDisallowChanges(variant.variantDslInfo.targetSdkVersion.convert())

                // FIXME resvalue
                if (variant is ApkCreationConfig) {
                    variantInput.manifestPlaceholders.setDisallowChanges(variant.manifestPlaceholders)
                }

                variantInput.resourceConfigurations.setDisallowChanges(variant.resourceConfigurations)
                // FIXME proguardFiles
                // FIXME consumerProguardFiles

                // FIXME sourceProviders
                // FIXME testSourceProviders

                if (variant is ApkCreationConfig) {
                    variantInput.debuggable.setDisallowChanges(variant.debuggable)
                }
            }
        }

        private fun getAndroidArtifact(
            componentImpl: ComponentImpl
        ): AndroidArtifactInput =
            globalScope.dslServices.newInstance(AndroidArtifactInput::class.java).also {
                it.applicationId.setDisallowChanges(componentImpl.applicationId)
                it.generatedSourceFolders.setDisallowChanges(listOf()) // FIXME
                it.generatedResourceFolders.setDisallowChanges(listOf()) //FIXME
                it.javacOutFolder.setDisallowChanges(componentImpl.artifacts.get(JAVAC).map { it.asFile })

                it.additionalClasses.from(
                    componentImpl.variantData.allPreJavacGeneratedBytecode
                )
                it.additionalClasses.from(componentImpl.variantData.allPostJavacGeneratedBytecode)
                it.additionalClasses.from(componentImpl
                    .getCompiledRClasses(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH))
                it.additionalClasses.disallowChanges()
            }

        private fun getJavaArtifact(
            properties: TestComponentImpl
        ): JavaArtifactInput =
            globalScope.dslServices.newInstance(JavaArtifactInput::class.java).also {
                it.javacOutFolder.setDisallowChanges(
                    properties.artifacts.get(JAVAC).map { it.asFile })
                it.additionalClasses.from(
                    properties.variantData.allPreJavacGeneratedBytecode
                )
                it.additionalClasses.from(properties.variantData.allPostJavacGeneratedBytecode)
                it.additionalClasses.from(properties
                    .getCompiledRClasses(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH))
                it.additionalClasses.disallowChanges()
            }

        private fun com.android.build.api.variant.AndroidVersion.convert(): SdkVersion =
            globalScope.dslServices.newInstance(SdkVersion::class.java).also {
                it.apiLevel.setDisallowChanges(apiLevel)
                it.codeName.setDisallowChanges(codename)
        }

        private fun ApiVersion.convert(): SdkVersion = globalScope.dslServices.newInstance(
            SdkVersion::class.java
        ).also {
            it.apiLevel.setDisallowChanges(apiLevel)
            it.codeName.setDisallowChanges(codename)
        }

        private fun <T : TestComponentImpl> getTestComponent(
            variant: VariantImpl,
            targetClass: Class<T>
        ): T? = testPropertiesList
            .asSequence()
            .filter { it.testedConfig === variant }
            .filterIsInstance(targetClass)
            .firstOrNull()
    }
}

private fun VariantType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        VariantTypeImpl.BASE_APK -> LintModelModuleType.APP
        VariantTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        VariantTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        VariantTypeImpl.TEST_APK -> LintModelModuleType.TEST
        else -> throw RuntimeException("Unsupported VariantTypeImpl value")
    }
}
