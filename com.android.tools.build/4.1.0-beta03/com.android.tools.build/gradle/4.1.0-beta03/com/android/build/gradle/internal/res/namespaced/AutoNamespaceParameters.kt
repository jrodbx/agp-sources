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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/** Parameters common to auto-namespacing transforms */
interface AutoNamespaceParameters: GenericTransformParameters {
    @get:Input
    val aapt2Version: Property<String>
    @get:Internal
    val aapt2FromMaven: ConfigurableFileCollection
    @get:Internal
    val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>
    @get:Internal
    val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
}
/** Initialize the auto namespacing parameters */
fun AutoNamespaceParameters.init(globalScope: GlobalScope) {
    val (file, version) = getAapt2FromMavenAndVersion(globalScope)
    aapt2FromMaven.from(file)
    aapt2Version.set(version)
    errorFormatMode.set(SyncOptions.getErrorFormatMode(globalScope.projectOptions))
    aapt2DaemonBuildService.setDisallowChanges(getBuildService(globalScope.project.gradle.sharedServices))
}