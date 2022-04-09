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

import com.android.build.api.dsl.SdkComponents
import com.android.build.gradle.internal.services.DslServices
import com.android.repository.Revision
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class SdkComponentsImpl @Inject constructor(
    dslServices: DslServices,
    compileSdkVersion: Provider<String>,
    buildToolsRevision: Provider<Revision>,
    val ndkVersion: Provider<String>,
    val ndkPath: Provider<String>,
    val bootclasspathProvider: Provider<Provider<List<RegularFile>>>
) : SdkComponents {

    override val sdkDirectory: Provider<Directory> =
        dslServices.sdkComponents.flatMap {
            it.sdkLoader(compileSdkVersion, buildToolsRevision).sdkDirectoryProvider }

    override val ndkDirectory: Provider<Directory> by lazy {
        dslServices.sdkComponents.flatMap {
            it.versionedNdkHandler(
                compileSdkVersion = compileSdkVersion.get(),
                ndkVersion = ndkVersion.get(),
                ndkPath = ndkPath.get()
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
}
