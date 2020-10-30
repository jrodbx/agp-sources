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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import com.android.resources.ResourceType
import com.android.sdklib.AndroidTargetHash
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File

open class GatherModuleInfoTask : DefaultTask() {
    private lateinit var sourceProjectName : String

    @get:OutputFile
    lateinit var outputProvider : Provider<RegularFile>
        private set

    private var moduleDataHolder = ModuleInfo()

    @TaskAction
    fun action() {
        AndroidCollector().collectInto(moduleDataHolder, this)
        DependencyCollector().collectInto(moduleDataHolder, this)
        KotlinUsageCollector().collectInto(moduleDataHolder, this)
        SourceFilesCollector().collectInto(moduleDataHolder, this)

        // Transform the ":" from the path into "_" so Poet can generate directories for each module
        // correctly. Example: :module1:submoduleA will become module1_submoduleA
        moduleDataHolder.name = sourceProjectName.replace(':', '_')
        moduleDataHolder.saveAsJsonTo(outputProvider.get().asFile)
    }

    class ConfigAction(private val project: Project) : Action<GatherModuleInfoTask> {
        override fun execute(task: GatherModuleInfoTask) {
            task.sourceProjectName = project.name
            task.outputProvider = project.layout.buildDirectory.file("local-module-info.json")
        }
    }
}

private interface DataCollector {
    fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask)
}

private class AndroidCollector : DataCollector {
    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        if (!task.project.isAndroidProject()) return
        dataHolder.type = ModuleType.ANDROID
        task.project.plugins.withType(BasePlugin::class.java).firstOrNull()?.let {
            val basePlugin: BasePlugin<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl> = it as BasePlugin<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
            collectBuildConfig(dataHolder, basePlugin)
            collectResources(dataHolder, basePlugin)
        }
    }

    private fun collectBuildConfig(
        dataHolder: ModuleInfo,
        plugin: BasePlugin<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
    ) {
        plugin.extension.defaultConfig.minSdkVersion?.apiLevel?.let {
            dataHolder.androidBuildConfig.minSdkVersion = it }
        plugin.extension.defaultConfig.targetSdkVersion?.apiLevel?.let {
            dataHolder.androidBuildConfig.targetSdkVersion = it }

        // extension.compileSdkVersion returns "android-27", we want just "27".
        dataHolder.androidBuildConfig.compileSdkVersion =
                AndroidTargetHash.getPlatformVersion(plugin.extension.compileSdkVersion!!)!!.apiLevel
    }

    private fun collectResources(
        dataHolder: ModuleInfo,
        plugin: BasePlugin<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
    ) {
        val resources = plugin.extension.sourceSets
            .findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.res ?: return

        resources.srcDirs.forEach {
            val symbolTable = parseResourceSourceSetDirectory(it, IdProvider.constant(), null)

            // TODO add more resource types as ASPoet supports them
            dataHolder.resources.imageCount +=
                    symbolTable.getSymbolByResourceType(ResourceType.DRAWABLE).size
            dataHolder.resources.layoutCount +=
                    symbolTable.getSymbolByResourceType(ResourceType.LAYOUT).size
            dataHolder.resources.stringCount +=
                    symbolTable.getSymbolByResourceType(ResourceType.STRING).size
        }
    }
}

private class SourceFilesCollector : DataCollector {
    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        // Get the java sources from main (excludes tests for now) or just return if they are
        // not there, since we do not have anything else to do.
        val javaPackageMap = mutableMapOf<String, Int>()
        val kotlinPackageMap = mutableMapOf<String, Int>()

        task.project.findMainSourceSet().forEach {
            it.walkBottomUp().toList().filter { !it.isDirectory }
                .forEach {
                    val dirPackage = it.parent
                    when (it.extension) {
                        "java" -> javaPackageMap.increment(dirPackage)
                        "kt" -> kotlinPackageMap.increment(dirPackage)
                    }
                }
        }

        dataHolder.javaSourceInfo.packages = javaPackageMap.size
        if (javaPackageMap.isNotEmpty()) {
            // Since ASPoet doesn't support count per package, we pick the biggest.
            dataHolder.javaSourceInfo.classesPerPackage = javaPackageMap.values.max() ?: 0
            dataHolder.javaSourceInfo.methodsPerClass = 10 // TODO actually count the methods
            dataHolder.javaSourceInfo.fieldsPerClass = 10 // TODO actually count the fields
        }

        dataHolder.kotlinSourceInfo.packages = kotlinPackageMap.size
        if (kotlinPackageMap.isNotEmpty()) {
            // Since ASPoet doesn't support count per package, we pick the biggest.
            dataHolder.kotlinSourceInfo.classesPerPackage = kotlinPackageMap.values.max() ?: 0
            dataHolder.kotlinSourceInfo.methodsPerClass = 10 // TODO actually count the methods
            dataHolder.kotlinSourceInfo.fieldsPerClass = 10 // TODO actually count the methods
        }
    }

    private fun Project.findMainSourceSet(): Set<File> {
        if (isAndroidProject()) {
            val androidPlugin = plugins.withType(BasePlugin::class.java).first() as BasePlugin<VariantImpl<VariantPropertiesImpl>,VariantPropertiesImpl>
            return androidPlugin.extension.sourceSets
                .findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.java?.srcDirs ?: emptySet()
        } else {
            val javaPlugin =
                convention.findPlugin(JavaPluginConvention::class.java) ?: return emptySet()
            return javaPlugin.sourceSets
                .findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.allSource?.srcDirs ?: emptySet()
        }
    }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }
}

private class KotlinUsageCollector : DataCollector {
    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        if (task.project.usesKotlin()) dataHolder.useKotlin = true
    }

    private fun Project.usesKotlin(): Boolean {
        return (isAndroidProject() && plugins.hasPlugin("kotlin-android")) ||
                plugins.hasPlugin("kotlin")
    }
}

private class DependencyCollector : DataCollector {
    val relevantConfigurations = setOf(
        "api", "implementation", "classpath",
        "compile", "compileOnly",
        "runtime", "runtimeOnly",
        "testImplementation", "testCompile",
        "androidTestImplementation", "androidTestCompile",
        "annotationProcessor", "kapt",
        "package", "provided", "wearApp"
    )

    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        val projectDependencies = task.project.configurations.asIterable()
            .filter { relevantConfigurations.contains(it.name) }
            .flatMap { gatherDependencies(it) }.asIterable()
            .cleanDependencies(task.project)

        dataHolder.dependencies.addAll(projectDependencies)
    }

    private fun gatherDependencies(config: Configuration): List<PoetDependenciesInfo> {
        val deps = mutableListOf<PoetDependenciesInfo>()

        for (dependency in config.allDependencies) when (dependency) {
                is ProjectDependency -> PoetDependenciesInfo(
                    DependencyType.MODULE,
                    config.name,
                    // For sub modules, drop the first ":" from the path and transform the others
                    // into "_" so Poet can generate directories for each module correctly.
                    dependency.dependencyProject.path.drop(1).replace(':', '_')
                )
                is ModuleDependency -> PoetDependenciesInfo(
                    DependencyType.EXTERNAL_LIBRARY,
                    config.name,
                    "${dependency.group}:${dependency.name}:${dependency.version}"
                )
                else -> null
            }?.let { deps.add(it) }
        return deps
    }

    private fun Iterable<PoetDependenciesInfo>.cleanDependencies(project: Project): Iterable<PoetDependenciesInfo> {
        var filteredDependencies = this

        // Java modules that have the "kotlin" plugin applied creates repeated dependencies for
        // kotlin jdk, so here we remove them to not pollute the resulting file.
        if (project.plugins.hasPlugin("java-library") && project.plugins.hasPlugin("kotlin")) {
            // Keep only the compile dependency if it's org.jetbrains.kotlin:kotlin-stdlib-jdk.
            filteredDependencies = filteredDependencies.filter {
                !it.dependency.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk") ||
                        it.scope == "compile"
            }
        }

        return filteredDependencies
    }

}

private fun Project.isAndroidProject(): Boolean {
    // "com.android.application", "com.android.library", "com.android.test"
    return plugins.withType(BasePlugin::class.java).count() > 0
}
