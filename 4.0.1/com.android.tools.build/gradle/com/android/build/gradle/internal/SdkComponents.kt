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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.cxx.stripping.createSymbolStripExecutableFinder
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.OptionalLibrary
import com.android.utils.ILogger
import com.google.common.base.Suppliers
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File
import java.util.function.Supplier

// TODO: Remove open, make it an interface and move impl to a separate class.
open class SdkComponents(
    project: Project,
    private val sdkLoadStrategy: SdkLoadingStrategy,
    private val sdkHandlerSupplier: Supplier<SdkHandler>,
    val ndkHandlerSupplier: Supplier<NdkHandler>,
    private val issueReporter: IssueReporter) {

    private val projectRootDir = project.rootDir

    companion object {
        fun createSdkComponents(
            project: Project,
            projectOptions: ProjectOptions,
            extensionSupplier: Supplier<BaseExtension>,
            logger: ILogger,
            issueReporter: IssueReporter): SdkComponents {

            val sdkSourceSet = SdkLocationSourceSet(project.rootDir)

            val sdkHandlerSupplier = Suppliers.memoize {
                val sdkHandler = SdkHandler(
                    sdkSourceSet,
                    logger,
                    issueReporter)
                sdkHandler.setSdkLibData(
                    SdkLibDataFactory(
                        !project.gradle.startParameter.isOffline && projectOptions.get(BooleanOption.ENABLE_SDK_DOWNLOAD),
                        projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL),
                        logger
                    ).getSdkLibData())
                sdkHandler
            }

            val fullScanLoadingStrategy = SdkFullLoadingStrategy(
                sdkHandlerSupplier,
                Supplier { extensionSupplier.get()?.compileSdkVersion },
                Supplier { extensionSupplier.get()?.buildToolsRevision },
                projectOptions.get(BooleanOption.USE_ANDROID_X))
            val directLoadingStrategy = SdkDirectLoadingStrategy(
                sdkSourceSet,
                Supplier { extensionSupplier.get()?.compileSdkVersion },
                Supplier { extensionSupplier.get()?.buildToolsRevision },
                projectOptions.get(BooleanOption.USE_ANDROID_X),
                issueReporter)

            val sdkLoadWithFallback = SdkLoadingStrategy(
                directLoadingStrategy, fullScanLoadingStrategy)

            val ndkHandlerSupplier = Suppliers.memoize {
                NdkHandler(
                    issueReporter,
                    projectOptions.get(BooleanOption.ENABLE_SIDE_BY_SIDE_NDK),
                    extensionSupplier.get()?.ndkVersion,
                    extensionSupplier.get()!!.compileSdkVersion!!,
                    project.rootDir)
            }

            return SdkComponents(
                project,
                sdkLoadWithFallback,
                sdkHandlerSupplier,
                ndkHandlerSupplier,
                issueReporter
            )
        }
    }

    val sdkSetupCorrectly: Provider<Boolean> = project.providers.provider {
        sdkLoadStrategy.getAndroidJar() != null && sdkLoadStrategy.getBuildToolsInfo() != null }

    val buildToolInfoProvider: Provider<BuildToolInfo> = project.providers.provider { sdkLoadStrategy.getBuildToolsInfo() }
    val buildToolsRevisionProvider: Provider<Revision> = project.providers.provider { sdkLoadStrategy.getBuildToolsRevision() }
    val aidlExecutableProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getAidlExecutable() }
    val adbExecutableProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getAdbExecutable() }
    val coreLambdaStubsProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getCoreLambaStubs() }
    val splitSelectExecutableProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getSplitSelectExecutable() }

    val androidJarProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getAndroidJar() }
    val annotationsJarProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getAnnotationsJar() }
    val aidlFrameworkProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getAidlFramework() }

    val additionalLibrariesProvider: Provider<List<OptionalLibrary>> = project.providers.provider { sdkLoadStrategy.getAdditionalLibraries() }
    val optionalLibrariesProvider: Provider<List<OptionalLibrary>> = project.providers.provider { sdkLoadStrategy.getOptionalLibraries() }
    val targetAndroidVersionProvider: Provider<AndroidVersion> = project.providers.provider { sdkLoadStrategy.getTargetPlatformVersion() }
    val targetBootClasspathProvider: Provider<List<File>> = project.providers.provider { sdkLoadStrategy.getTargetBootClasspath() }

    val renderScriptSupportJarProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getRenderScriptSupportJar() }
    val supportNativeLibFolderProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getSupportNativeLibFolder() }
    val supportBlasLibFolderProvider: Provider<File> = project.providers.provider { sdkLoadStrategy.getSupportBlasLibFolder() }

    val ndkFolderProvider: Provider<File> = project.providers.provider { ndkHandlerSupplier.get().ndkPlatform.getOrThrow().ndkDirectory }
    val stripExecutableFinderProvider: Provider<SymbolStripExecutableFinder> =
        project.providers.provider { createSymbolStripExecutableFinder(ndkHandlerSupplier.get()) }

    @Synchronized
    fun unload() {
        sdkLoadStrategy.reset()
    }

    // These old methods are expensive and require SDK Parsing or some kind of installation/download.
    // TODO: Add mechanism to warn if those are called during configuration time.

    fun installNdk(ndkHandler: NdkHandler) {
        sdkHandlerSupplier.get().installNdk(ndkHandler)
    }

    fun installCmake(version: String) {
        sdkHandlerSupplier.get().installCMake(version)
    }

    fun getSdkDirectory(): File {
        return SdkLocator.getSdkDirectory(projectRootDir, issueReporter)
    }
}
