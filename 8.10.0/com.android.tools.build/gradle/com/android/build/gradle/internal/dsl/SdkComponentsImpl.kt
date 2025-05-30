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

package com.android.build.gradle.internal.dsl

import com.android.SdkConstants.FN_AAPT2
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.variant.Aapt2
import com.android.build.api.variant.Aidl
import com.android.build.gradle.internal.res.Aapt2FromMaven
import com.android.build.gradle.internal.services.DslServices
import com.android.repository.Revision
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import javax.inject.Inject

open class SdkComponentsImpl @Inject constructor(
    dslServices: DslServices,
    compileSdkVersion: Provider<String>,
    buildToolsRevision: Provider<Revision>,
    val ndkVersion: Provider<String>,
    val ndkPath: Provider<String>,
    val bootclasspathProvider: Provider<Provider<List<RegularFile>>>,
    providerFactory: ProviderFactory,
    project: Project
) : SdkComponents {

    override val sdkDirectory: Provider<Directory> =
        dslServices.sdkComponents.flatMap {
            it.sdkLoader(compileSdkVersion, buildToolsRevision).sdkDirectoryProvider }

    override val ndkDirectory: Provider<Directory> by lazy {
        dslServices.sdkComponents.flatMap {
            it.versionedNdkHandler(
                ndkVersion = ndkVersion.get(),
                ndkPathFromDsl = if (ndkPath.isPresent) ndkPath.get() else null
            ).ndkDirectoryProvider
        }
    }
    override val adb: Provider<RegularFile> =
        dslServices.sdkComponents.flatMap {
            it.sdkLoader(compileSdkVersion, buildToolsRevision)
                .adbExecutableProvider
        }
    override val bootClasspath: Provider<List<RegularFile>>
        get() = bootclasspathProvider.get()

    override val aidl: Provider<Aidl> by lazy(LazyThreadSafetyMode.NONE) {
        val aidlExecutable = dslServices.sdkComponents.flatMap {
            it.sdkLoader(compileSdkVersion, buildToolsRevision).aidlExecutableProvider
        }

        val aidlFramework = dslServices.sdkComponents.flatMap {
            it.sdkLoader(compileSdkVersion, buildToolsRevision).aidlFrameworkProvider
        }

        dslServices.provider(
            Aidl::class.java,
            DefaultAidl(
                aidlExecutable,
                aidlFramework,
                buildToolsRevision.map { it.toString() }
            )
        )
    }

    override val aapt2: Provider<Aapt2> by lazy(LazyThreadSafetyMode.NONE) {
        providerFactory.provider {
            Aapt2FromMaven.create(project) { System.getenv(it.propertyName) }
        }.map { aapt2FromMaven ->
            DefaultAapt(
                executable = project.objects.fileProperty().fileProvider(
                    providerFactory.provider {
                        File(aapt2FromMaven.aapt2Directory.singleFile, FN_AAPT2)
                    }
                ),
                version = providerFactory.provider {
                    aapt2FromMaven.version
                }
            )
        }
    }
}
