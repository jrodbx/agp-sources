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
import com.android.build.gradle.internal.ide.CustomSourceDirectoryImpl
import com.android.builder.model.v2.CustomSourceDirectory
import com.android.builder.model.SourceProvider
import com.google.common.base.CaseFormat
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import java.io.File
import javax.inject.Inject

open class DefaultAndroidSourceSet @Inject constructor(
    name: String,
    // Avoid using, this is needed only to allow applying Closure(s).
    private val project: Project,
    private val publishPackage: Boolean
) : AndroidSourceSet, SourceProvider {
    private val sourceSetName = AndroidSourceSetName(name)

    final override val java: AndroidSourceDirectorySet
    final override val kotlin: com.android.build.api.dsl.AndroidSourceDirectorySet
    final override val resources: AndroidSourceDirectorySet
    final override val manifest: AndroidSourceFile
    final override val assets: AndroidSourceDirectorySet
    final override val res: AndroidSourceDirectorySet
    final override val aidl: AndroidSourceDirectorySet
    final override val renderscript: AndroidSourceDirectorySet
    final override val baselineProfiles: com.android.build.api.dsl.AndroidSourceDirectorySet
    @Deprecated("Unused")
    final override val jni: AndroidSourceDirectorySet
    final override val jniLibs: AndroidSourceDirectorySet
    final override val shaders: AndroidSourceDirectorySet
    final override val mlModels: AndroidSourceDirectorySet
    private val displayName : String = convertNameToDisplayName()

    init {
        java = DefaultAndroidSourceDirectorySet(
            displayName, "Java source", project, SourceArtifactType.JAVA_SOURCES
        )
        java.filter.include("**/*.java")

        kotlin = DefaultAndroidSourceDirectorySet(
                displayName, "Kotlin source", project, SourceArtifactType.KOTLIN_SOURCES
        )
        kotlin.filter.include("**/*.kt", "**/*.kts")

        resources = DefaultAndroidSourceDirectorySet(
            displayName,
            "Java resources",
            project,
            SourceArtifactType.JAVA_RESOURCES
        )
        resources.filter.exclude("**/*.java", "**/*.kt")

        manifest = DefaultAndroidSourceFile("$displayName manifest", project)

        assets = DefaultAndroidSourceDirectorySet(
            displayName, "assets", project, SourceArtifactType.ASSETS
        )

        res = DefaultAndroidSourceDirectorySet(
            displayName,
            "resources",
            project,
            SourceArtifactType.ANDROID_RESOURCES
        )

        aidl = DefaultAndroidSourceDirectorySet(
            displayName, "aidl", project, SourceArtifactType.AIDL
        )

        renderscript = DefaultAndroidSourceDirectorySet(
            displayName,
            "renderscript",
            project,
            SourceArtifactType.RENDERSCRIPT
        )

        baselineProfiles = DefaultAndroidSourceDirectorySet(
            displayName,
            "baselineProfiles",
            project,
            SourceArtifactType.BASELINE_PROFILES
        )
        baselineProfiles.filter.include("**/*.txt")

        jni = DefaultAndroidSourceDirectorySet(
            displayName, "jni", project, SourceArtifactType.JNI
        )

        jniLibs = DefaultAndroidSourceDirectorySet(
            displayName, "jniLibs", project, SourceArtifactType.JNI_LIBS
        )

        shaders = DefaultAndroidSourceDirectorySet(
            displayName, "shaders", project, SourceArtifactType.SHADERS
        )

        mlModels = DefaultAndroidSourceDirectorySet(
            displayName, "ML models", project, SourceArtifactType.ML_MODELS
        )

        initRoot("src/${sourceSetName.name}")
    }

    override fun getName(): String {
        return sourceSetName.name
    }

    override fun toString(): String {
        return "source set $displayName"
    }

    override val apiConfigurationName = sourceSetName.apiConfigurationName

    override val compileOnlyConfigurationName = sourceSetName.compileOnlyConfigurationName

    override val compileOnlyApiConfigurationName = sourceSetName.compileOnlyApiConfigurationName

    override val implementationConfigurationName = sourceSetName.implementationConfigurationName

    override val runtimeOnlyConfigurationName = sourceSetName.runtimeOnlyConfigurationName

    @Suppress("OverridingDeprecatedMember")
    override val compileConfigurationName = sourceSetName.compileConfigurationName

    @Suppress("OverridingDeprecatedMember")
    override val packageConfigurationName: String
        get() {
            return if (publishPackage) {
                sourceSetName.publishedPackageConfigurationName
            } else {
                sourceSetName.packageConfigurationName
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override val providedConfigurationName = sourceSetName.providedConfigurationName

    override val wearAppConfigurationName = sourceSetName.wearAppConfigurationName

    override val annotationProcessorConfigurationName =
        sourceSetName.annotationProcessorConfigurationName

    override fun manifest(action: com.android.build.api.dsl.AndroidSourceFile.() -> Unit) {
        action.invoke(manifest)
    }

    override fun manifest(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(manifest, configureClosure)
        return this
    }

    override fun res(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(res)
    }

    override fun res(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(res, configureClosure)
        return this
    }

    override fun assets(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(assets)
    }

    override fun assets(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(assets, configureClosure)
        return this
    }

    override fun aidl(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(aidl)
    }

    override fun aidl(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(aidl, configureClosure)
        return this
    }

    override fun renderscript(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(renderscript)
    }

    override fun renderscript(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(renderscript, configureClosure)
        return this
    }

    override fun jni(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(jni)
    }

    override fun jni(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(jni, configureClosure)
        return this
    }

    override fun jniLibs(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(jniLibs)
    }

    override fun jniLibs(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(jniLibs, configureClosure)
        return this
    }

    override fun shaders(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(shaders)
    }

    override fun shaders(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(shaders, configureClosure)
        return this
    }

    override fun mlModels(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(mlModels)
    }

    override fun mlModels(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(mlModels, configureClosure)
        return this
    }

    override fun java(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(java)
    }

    override fun java(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(java, configureClosure)
        return this
    }

    override fun kotlin(action: Action<com.android.build.api.dsl.AndroidSourceDirectorySet>) {
        action.execute(kotlin)
    }

    override fun resources(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(resources)
    }

    override fun resources(configureClosure: Closure<*>): AndroidSourceSet {
        project.configure(resources, configureClosure)
        return this
    }

    override fun baselineProfiles(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
        action.invoke(baselineProfiles)
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
        baselineProfiles.setSrcDirs(listOf("$path/baselineProfiles"))
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

    // needed for IDE model implementation, not part of the DSL.
    override fun getCustomDirectories(): List<CustomSourceDirectory> {
        return extras.asMap.values.groupBy(
            DefaultAndroidSourceDirectorySet::getSourceSetName,
            DefaultAndroidSourceDirectorySet::srcDirs
        ).map {
            // there can be only one directory per source set since we do not allow to have
            // access to the extras field to end users (see below).
            CustomSourceDirectoryImpl(
                it.key,
                it.value.flatten().single(),
            )
        }
    }

    /**
     * Internal API to add customs folders to the DSL. Custom folders cannot be added through the
     * public [com.android.build.api.dsl.AndroidSourceSet] interface because it would allow users
     * to add a custom directory for a specific source set like 'main' or 'debug'.
     *
     * Since users cannot add have access to the created [DefaultAndroidSourceDirectorySet] for the
     * source types, they cannot add to it. Therefore there can only be one source directory per
     * custom source type on an Android Source Set.
     *
     * Users need to the use the public [AndroidComponents.registerSourceSet] API so
     * custom sources are added to all the source sets of the application uniformly.
     */
    internal val extras: NamedDomainObjectContainer<DefaultAndroidSourceDirectorySet> =
        project.objects.domainObjectContainer(
            DefaultAndroidSourceDirectorySet::class.java,
            AndroidSourceDirectorySetFactory(project, displayName, sourceSetName.name)
        )

    class AndroidSourceDirectorySetFactory(
        private val project: Project,
        private val sourceSetDisplayName: String,
        private val sourceSetName: String,
    ): NamedDomainObjectFactory<DefaultAndroidSourceDirectorySet> {

        override fun create(name: String): DefaultAndroidSourceDirectorySet {
            return DefaultAndroidSourceDirectorySet(
                sourceSetDisplayName,
                name,
                project,
                SourceArtifactType.CUSTOMS).also {
                    it.srcDir("src/$sourceSetName/$name")
            }
        }
    }

    /** Converts name to display name e.g. "fooDebug" (camel case) to "foo debug". */
    private fun convertNameToDisplayName(): String {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, sourceSetName.name).replace("-", " ")
    }
}
