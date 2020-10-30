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
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_ANNOTATION_PROCESSOR
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_API
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_APK
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_IMPLEMENTATION
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PROVIDED
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PUBLISH
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_RUNTIME_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_WEAR_APP
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import groovy.lang.Closure
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

    private val javaSource: AndroidSourceDirectorySet
    private val javaResources: AndroidSourceDirectorySet
    private val manifest: AndroidSourceFile
    private val assets: AndroidSourceDirectorySet
    private val res: AndroidSourceDirectorySet
    private val aidl: AndroidSourceDirectorySet
    private val renderscript: AndroidSourceDirectorySet
    private val jni: AndroidSourceDirectorySet
    private val jniLibs: AndroidSourceDirectorySet
    private val shaders: AndroidSourceDirectorySet
    private val displayName : String = GUtil.toWords(this.name)

    init {
        javaSource = DefaultAndroidSourceDirectorySet(
            "$displayName Java source", project, SourceArtifactType.JAVA_SOURCES
        )
        javaSource.filter.include("**/*.java")

        javaResources = DefaultAndroidSourceDirectorySet(
            "$displayName Java resources",
            project,
            SourceArtifactType.JAVA_RESOURCES
        )
        javaResources.filter.exclude("**/*.java", "**/*.kt")

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

    override fun getApiConfigurationName() = getName(CONFIG_NAME_API)

    override fun getCompileOnlyConfigurationName() = getName(CONFIG_NAME_COMPILE_ONLY)

    override fun getImplementationConfigurationName() = getName(CONFIG_NAME_IMPLEMENTATION)

    override fun getRuntimeOnlyConfigurationName() = getName(CONFIG_NAME_RUNTIME_ONLY)

    @Suppress("OverridingDeprecatedMember")
    override fun getCompileConfigurationName() = getName(CONFIG_NAME_COMPILE)

    @Suppress("OverridingDeprecatedMember")
    override fun getPackageConfigurationName(): String {
        if (publishPackage) {
            return getName(CONFIG_NAME_PUBLISH)
        }

        return getName(CONFIG_NAME_APK)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun getProvidedConfigurationName() = getName(CONFIG_NAME_PROVIDED)

    override fun getWearAppConfigurationName() = getName(CONFIG_NAME_WEAR_APP)

    override fun getAnnotationProcessorConfigurationName()
            = getName(CONFIG_NAME_ANNOTATION_PROCESSOR)

    override fun getManifest(): AndroidSourceFile {
        return manifest
    }

    override fun manifest(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getManifest())
        return this
    }

    override fun getRes(): AndroidSourceDirectorySet {
        return res
    }

    override fun res(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getRes())
        return this
    }

    override fun getAssets(): AndroidSourceDirectorySet {
        return assets
    }

    override fun assets(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getAssets())
        return this
    }

    override fun getAidl(): AndroidSourceDirectorySet {
        return aidl
    }

    override fun aidl(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getAidl())
        return this
    }

    override fun getRenderscript(): AndroidSourceDirectorySet {
        return renderscript
    }

    override fun renderscript(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getRenderscript())
        return this
    }

    override fun getJni(): AndroidSourceDirectorySet {
        return jni
    }

    override fun jni(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getJni())
        return this
    }

    override fun getJniLibs(): AndroidSourceDirectorySet {
        return jniLibs
    }

    override fun jniLibs(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getJniLibs())
        return this
    }

    override fun shaders(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, getShaders())
        return this
    }

    override fun getShaders(): AndroidSourceDirectorySet {
        return shaders
    }

    override fun getJava(): AndroidSourceDirectorySet {
        return javaSource
    }

    override fun java(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, java)
        return this
    }

    override fun getResources(): AndroidSourceDirectorySet {
        return javaResources
    }

    override fun resources(configureClosure: Closure<*>): AndroidSourceSet {
        ConfigureUtil.configure(configureClosure, resources)
        return this
    }

    override fun setRoot(path: String): AndroidSourceSet {
        return initRoot(path)
    }

    private fun initRoot(path: String): AndroidSourceSet {
        javaSource.setSrcDirs(listOf("$path/java"))
        javaResources.setSrcDirs(listOf("$path/resources"))
        res.setSrcDirs(listOf("$path/${SdkConstants.FD_RES}"))
        assets.setSrcDirs(listOf("$path/${SdkConstants.FD_ASSETS}"))
        manifest.srcFile("$path/${SdkConstants.FN_ANDROID_MANIFEST_XML}")
        aidl.setSrcDirs(listOf("$path/aidl"))
        renderscript.setSrcDirs(listOf("$path/rs"))
        jni.setSrcDirs(listOf("$path/jni"))
        jniLibs.setSrcDirs(listOf("$path/jniLibs"))
        shaders.setSrcDirs(listOf("$path/shaders"))
        return this
    }

    // --- SourceProvider

    override fun getJavaDirectories(): Set<File> {
        return java.srcDirs
    }

    override fun getResourcesDirectories(): Set<File> {
        return resources.srcDirs
    }

    override fun getManifestFile(): File {
        return getManifest().srcFile
    }

    override fun getAidlDirectories(): Set<File> {
        return getAidl().srcDirs
    }

    override fun getRenderscriptDirectories(): Set<File> {
        return getRenderscript().srcDirs
    }

    override fun getCDirectories(): Set<File> {
        return getJni().srcDirs
    }

    override fun getCppDirectories(): Set<File> {
        // The C and C++ directories are currently the same.  This may change in the future when
        // we use Gradle's native source sets.
        return getJni().srcDirs
    }

    override fun getResDirectories(): Set<File> {
        return getRes().srcDirs
    }

    override fun getAssetsDirectories(): Set<File> {
        return getAssets().srcDirs
    }

    override fun getJniLibsDirectories(): Collection<File> {
        return getJniLibs().srcDirs
    }

    override fun getShadersDirectories(): Collection<File> {
        return getShaders().srcDirs
    }
}
