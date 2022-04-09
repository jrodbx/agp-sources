/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.api

import com.android.SdkConstants
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceFile
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_ANNOTATION_PROCESSOR
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_API
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_APK
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_COMPILE
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_COMPILE_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_IMPLEMENTATION
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_PROVIDED
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_PUBLISH
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_RUNTIME_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_WEAR_APP
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.util.ConfigureUtil
import org.gradle.util.GUtil
import java.io.File
import javax.inject.Inject

open class DefaultAndroidSourceSet @Inject constructor(
    private val name: String,
    project: Project,
    private val publishPackage: Boolean
) : AndroidSourceSet, SourceProvider {

    final override val java: AndroidSourceDirectorySet
    final override val kotlin: com.android.build.api.dsl.AndroidSourceDirectorySet
    final override val resources: AndroidSourceDirectorySet
    final override val manifest: AndroidSourceFile
    final override val assets: AndroidSourceDirectorySet
    final override val res: AndroidSourceDirectorySet
    final override val aidl: AndroidSourceDirectorySet
    final override val renderscript: AndroidSourceDirectorySet
    @Deprecated("Unused")
    final override val jni: AndroidSourceDirectorySet
    final override val jniLibs: AndroidSourceDirectorySet
    final override val shaders: AndroidSourceDirectorySet
    final override val mlModels: AndroidSourceDirectorySet
    private val displayName : String = GUtil.toWords(this.name)

    init {
        java = DefaultAndroidSourceDirectorySet(
            "$displayName Java source", project, SourceArtifactType.JAVA_SOURCES
        )
        java.filter.include("**/*.java")

        kotlin = DefaultAndroidSourceDirectorySet(
                "$displayName Kotlin source", project, SourceArtifactType.KOTLIN_SOURCES
        )
        kotlin.filter.include("**/*.kt", "**/*.kts")

        resources = DefaultAndroidSourceDirectorySet(
            "$displayName Java resources",
            project,
            SourceArtifactType.JAVA_RESOURCES
        )
        resources.filter.exclude("**/*.java", "**/*.kt")

        manifest = DefaultAndroidSourceFile("$displayName manifest", project)

        assets = DefaultAndroidSourceDirectorySet(
            "$displayName assets", project, SourceArtifactType.ASSETS
        )

        res = DefaultAndroidSourceDirectorySet(
            "$displayName resources",
            project,
            SourceArtifactType.ANDROID_RESOURCES
        )

        aidl = DefaultAndroidSourceDirectorySet(
            "$displayName aidl", project, SourceArtifactType.AIDL
        )

        renderscript = DefaultAndroidSourceDirectorySet(
            "$displayName renderscript",
            project,
            SourceArtifactType.RENDERSCRIPT
        )

        jni = DefaultAndroidSourceDirectorySet(
            "$displayName jni", project, SourceArtifactType.JNI
        )

        jniLibs = DefaultAndroidSourceDirectorySet(
            "$displayName jniLibs", project, SourceArtifactType.JNI_LIBS
        )

        shaders = DefaultAndroidSourceDirectorySet(
            "$displayName shaders", project, SourceArtifactType.SHADERS
        )

        mlModels = DefaultAndroidSourceDirectorySet(
            "$displayName ML models", project, SourceArtifactType.ML_MODELS
        )

        initRoot("src/$name")
    }

    override fun getName(): String {
        return name
    }

    override fun toString(): String {
        return "source set $displayName"
    }

    private fun getName(config: String): String {
        return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            config
        } else {
            name.appendCapitalized(config)
        }
    }

    override val apiConfigurationName: String
        get() = getName(CONFIG_NAME_API)

    override val compileOnlyConfigurationName: String
        get() = getName(CONFIG_NAME_COMPILE_ONLY)

    override val implementationConfigurationName: String
        get() = getName(CONFIG_NAME_IMPLEMENTATION)

    override val runtimeOnlyConfigurationName: String
        get() = getName(CONFIG_NAME_RUNTIME_ONLY)

    @Suppress("OverridingDeprecatedMember")
    override val compileConfigurationName: String
        get() = getName(CONFIG_NAME_COMPILE)

    @Suppress("OverridingDeprecatedMember")
    override val packageConfigurationName: String
        get() {
        if (publishPackage) {
            return getName(CONFIG_NAME_PUBLISH)
        }

        return getName(CONFIG_NAME_APK)
    }

    @Suppress("OverridingDeprecatedMember")
    override val providedConfigurationName = getName(CONFIG_NAME_PROVIDED)

    override val wearAppConfigurationName = getName(CONFIG_NAME_WEAR_APP)

    override val annotationProcessorConfigurationName
    get()
            = getName(CONFIG_NAME_ANNOTATION_PROCESSOR)

    override fun manifest(action: com.android.build.api.dsl.AndroidSourceFile.() -> Unit) {
        action.invoke(manifest)
    }

    override fun manifest(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, manifest)
        return this
    }

    override fun res(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(res)
    }

    override fun res(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, res)
        return this
    }

    override fun assets(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(assets)
    }

    override fun assets(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, assets)
        return this
    }

    override fun aidl(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(aidl)
    }

    override fun aidl(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, aidl)
        return this
    }

    override fun renderscript(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(renderscript)
    }

    override fun renderscript(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, renderscript)
        return this
    }

    override fun jni(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(jni)
    }

    override fun jni(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, jni)
        return this
    }

    override fun jniLibs(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(jniLibs)
    }

    override fun jniLibs(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, jniLibs)
        return this
    }

    override fun shaders(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(shaders)
    }

    override fun shaders(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, shaders)
        return this
    }

    override fun mlModels(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(mlModels)
    }

    override fun mlModels(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, mlModels)
        return this
    }

    override fun java(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(java)
    }

    override fun java(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, java)
        return this
    }

    override fun kotlin(action: Action<com.android.build.api.dsl.AndroidSourceDirectorySet>) {
        action.execute(kotlin)
    }

    override fun resources(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(resources)
    }

    override fun resources(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, resources)
        return this
    }

    override fun setRoot(path: String): AndroidSourceSet {
        return initRoot(path)
    }

    private fun initRoot(path: String): AndroidSourceSet {
        java.setSrcDirs(listOf("$path/java"))
        kotlin.setSrcDirs(listOf("$path/java", "$path/kotlin"))
        resources.setSrcDirs(listOf("$path/resources"))
        res.setSrcDirs(listOf("$path/${SdkConstants.FD_RES}"))
        assets.setSrcDirs(listOf("$path/${SdkConstants.FD_ASSETS}"))
        manifest.srcFile("$path/${SdkConstants.FN_ANDROID_MANIFEST_XML}")
        aidl.setSrcDirs(listOf("$path/aidl"))
        renderscript.setSrcDirs(listOf("$path/rs"))
        jni.setSrcDirs(listOf("$path/jni"))
        jniLibs.setSrcDirs(listOf("$path/jniLibs"))
        shaders.setSrcDirs(listOf("$path/shaders"))
        mlModels.setSrcDirs(listOf("$path/${SdkConstants.FD_ML_MODELS}"))
        return this
    }

    // --- SourceProvider

    override fun getJavaDirectories(): Set<File> {
        return java.srcDirs
    }

    override fun getKotlinDirectories(): Set<File> {
        return (kotlin as DefaultAndroidSourceDirectorySet).srcDirs
    }

    override fun getResourcesDirectories(): Set<File> {
        return resources.srcDirs
    }

    override fun getManifestFile(): File {
        return manifest.srcFile
    }

    override fun getAidlDirectories(): Set<File> {
        return aidl.srcDirs
    }

    override fun getRenderscriptDirectories(): Set<File> {
        return renderscript.srcDirs
    }

    override fun getCDirectories(): Set<File> {
        return jni.srcDirs
    }

    override fun getCppDirectories(): Set<File> {
        // The C and C++ directories are currently the same.  This may change in the future when
        // we use Gradle's native source sets.
        return jni.srcDirs
    }

    override fun getResDirectories(): Set<File> {
        return res.srcDirs
    }

    override fun getAssetsDirectories(): Set<File> {
        return assets.srcDirs
    }

    override fun getJniLibsDirectories(): Collection<File> {
        return jniLibs.srcDirs
    }

    override fun getShadersDirectories(): Collection<File> {
        return shaders.srcDirs
    }

    override fun getMlModelsDirectories(): Collection<File> {
        return mlModels.srcDirs
    }
}
