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

package com.android.build.gradle.internal

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.NdkLocator
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.cxx.stripping.createSymbolStripExecutableFinder
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.OptionalLibrary
import org.gradle.api.NonExtensible
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import javax.inject.Inject

/**
 * Build service used to load SDK components. All build operations requiring access to the SDK
 * components should declare it as input.
 */
abstract class SdkComponentsBuildService @Inject constructor(
    objectFactory: ObjectFactory,
    providerFactory: ProviderFactory
) :
    BuildService<SdkComponentsBuildService.Parameters>, AutoCloseable {

    interface Parameters : BuildServiceParameters {
        val projectRootDir: RegularFileProperty
        val offlineMode: Property<Boolean>
        val issueReporter: Property<SyncIssueReporterImpl.GlobalSyncIssueService>

        val enableSdkDownload: Property<Boolean>
        val androidSdkChannel: Property<Int>
        val useAndroidX: Property<Boolean>

        val ndkVersion: Property<String>
        val ndkPath: Property<String>

        val compileSdkVersion: Property<String>
        val buildToolsRevision: Property<Revision>
    }

    private val sdkSourceSet: SdkLocationSourceSet by lazy {
        SdkLocationSourceSet(parameters.projectRootDir.get().asFile)
    }

    private val sdkHandler: SdkHandler by lazy {
        SdkHandler(sdkSourceSet, parameters.issueReporter.get()).also {
            it.setSdkLibData(
                SdkLibDataFactory(
                    !parameters.offlineMode.get() && parameters.enableSdkDownload.get(),
                    parameters.androidSdkChannel.orNull,
                    LoggerWrapper.getLogger(SdkLibDataFactory::class.java)
                ).getSdkLibData()
            )
        }
    }

    private val sdkLoadStrategy: SdkLoadingStrategy by lazy {
        val fullScanLoadingStrategy = SdkFullLoadingStrategy(
            sdkHandler,
            parameters.compileSdkVersion.orNull,
            parameters.buildToolsRevision.orNull,
            parameters.useAndroidX.get()
        )
        val directLoadingStrategy = SdkDirectLoadingStrategy(
            sdkSourceSet,
            parameters.compileSdkVersion.orNull,
            parameters.buildToolsRevision.orNull,
            parameters.useAndroidX.get(),
            parameters.issueReporter.get()
        )

        SdkLoadingStrategy(
            directLoadingStrategy, fullScanLoadingStrategy
        )
    }

    private val ndkLocator: NdkLocator by lazy {
        NdkLocator(
            parameters.issueReporter.get(),
            parameters.ndkVersion.orNull,
            parameters.ndkPath.orNull,
            parameters.projectRootDir.get().asFile,
            sdkHandler
        )
    }

    val ndkHandler: NdkHandler by lazy {
        NdkHandler(ndkLocator, parameters.compileSdkVersion.get())
    }

    override fun close() {
        synchronized(sdkLoadStrategy) {
            sdkLoadStrategy.reset()
        }
        SdkLocator.resetCache()
    }

    val sdkSetupCorrectly: Provider<Boolean> = providerFactory.provider {
        sdkLoadStrategy.getAndroidJar() != null && sdkLoadStrategy.getBuildToolsInfo() != null
    }

    val buildToolInfoProvider: Provider<BuildToolInfo> = providerFactory.provider {
        sdkLoadStrategy.getBuildToolsInfo()
    }

    val buildToolsRevisionProvider: Provider<Revision> = providerFactory.provider {
        sdkLoadStrategy.getBuildToolsRevision()
    }

    val aidlExecutableProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getAidlExecutable()
    }

    val adbExecutableProvider: Provider<RegularFile> = objectFactory.fileProperty().fileProvider(
        providerFactory.provider { sdkLoadStrategy.getAdbExecutable() }
    )

    val coreLambdaStubsProvider: Provider<RegularFile> = objectFactory.fileProperty().fileProvider(
        providerFactory.provider { sdkLoadStrategy.getCoreLambaStubs() }
    )

    val splitSelectExecutableProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getSplitSelectExecutable()
    }

    val androidJarProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getAndroidJar()
    }
    val annotationsJarProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getAnnotationsJar()
    }
    val aidlFrameworkProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getAidlFramework()
    }

    val additionalLibrariesProvider: Provider<List<OptionalLibrary>> = providerFactory.provider {
        sdkLoadStrategy.getAdditionalLibraries()
    }

    val optionalLibrariesProvider: Provider<List<OptionalLibrary>> = providerFactory.provider {
        sdkLoadStrategy.getOptionalLibraries()
    }
    val targetAndroidVersionProvider: Provider<AndroidVersion> = providerFactory.provider {
        sdkLoadStrategy.getTargetPlatformVersion()
    }

    val targetBootClasspathProvider: Provider<List<File>> = providerFactory.provider {
        sdkLoadStrategy.getTargetBootClasspath()
    }

    val renderScriptSupportJarProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getRenderScriptSupportJar()
    }
    val supportNativeLibFolderProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getSupportNativeLibFolder()
    }
    val supportBlasLibFolderProvider: Provider<File> = providerFactory.provider {
        sdkLoadStrategy.getSupportBlasLibFolder()
    }

    val sdkDirectoryProvider: Provider<Directory> =
        objectFactory.directoryProperty().fileProvider(providerFactory.provider {
            getSdkDir(
                parameters.projectRootDir.get().asFile,
                parameters.issueReporter.get()
            )
        })

    val ndkDirectoryProvider: Provider<Directory> =
        objectFactory.directoryProperty().fileProvider(providerFactory.provider {
            ndkHandler.ndkPlatform.getOrThrow().ndkDirectory
        })

    val ndkRevisionProvider: Provider<Revision> =
        providerFactory.provider { ndkHandler.ndkPlatform.getOrThrow().revision }

    val stripExecutableFinderProvider: Provider<SymbolStripExecutableFinder> =
        providerFactory.provider {
            createSymbolStripExecutableFinder(ndkHandler)
        }
    val objcopyExecutableMapProvider: Provider<Map<Abi, File>> = providerFactory.provider {
        val ndkHandler = ndkHandler
        if (!ndkHandler.ndkPlatform.isConfigured) {
            return@provider mapOf<Abi, File>()
        }
        val objcopyExecutables = mutableMapOf<Abi, File>()
        for (abi in ndkHandler.ndkPlatform.getOrThrow().supportedAbis) {
            objcopyExecutables[abi] =
                ndkHandler.ndkPlatform.getOrThrow().ndkInfo.getObjcopyExecutable(abi)
        }
        return@provider objcopyExecutables
    }

    // These old methods are expensive and require SDK Parsing or some kind of installation/download.

    fun installCmake(version: String) {
        sdkHandler.installCMake(version)
    }

    class RegistrationAction(
        project: Project,
        private val projectOptions: ProjectOptions,
        private val compileSdkVersion: Provider<String>,
        private val buildToolsRevision: Provider<Revision>,
        private val ndkVersion: Provider<String>,
        private val ndkPath: Provider<String>
    ) : ServiceRegistrationAction<SdkComponentsBuildService, Parameters>(
        project,
        SdkComponentsBuildService::class.java
    ) {

        override fun configure(parameters: Parameters) {
            parameters.projectRootDir.set(project.rootDir)
            parameters.offlineMode.set(project.gradle.startParameter.isOffline)
            parameters.issueReporter.set(getBuildService(project.gradle.sharedServices))

            parameters.enableSdkDownload.set(projectOptions.get(BooleanOption.ENABLE_SDK_DOWNLOAD))
            parameters.androidSdkChannel.set(projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL))
            parameters.useAndroidX.set(projectOptions.get(BooleanOption.USE_ANDROID_X))

            parameters.compileSdkVersion.set(compileSdkVersion)
            parameters.buildToolsRevision.set(buildToolsRevision)
            parameters.ndkVersion.set(ndkVersion)
            parameters.ndkPath.set(ndkPath)
        }
    }
}

/**
 * This avoids invoking [SdkComponentsBuildService.sdkDirectoryProvider] in order to get the SDK
 * location. This is needed when AGP handles missing [com.android.build.gradle.BaseExtension.compileSdkVersion]
 * and it tries to add highest installed API when invoked from the IDE.
 */
fun getSdkDir(projectRootDir: File, issueReporter: IssueReporter): File {
    return SdkLocator.getSdkDirectory(projectRootDir, issueReporter)
}

/** This can be used by tasks requiring android.jar as input with [org.gradle.api.tasks.Nested]. */
@NonExtensible
abstract class AndroidJarInput {
    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    fun getAndroidJar(): Provider<File> = sdkBuildService.flatMap { it.androidJarProvider }
}

/** This can be used by tasks requiring adb executable as input with [org.gradle.api.tasks.Nested]. */
@NonExtensible
abstract class AdbExecutableInput {
    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    fun getAdbExecutable(): Provider<RegularFile> =
        sdkBuildService.flatMap { it.adbExecutableProvider }
}