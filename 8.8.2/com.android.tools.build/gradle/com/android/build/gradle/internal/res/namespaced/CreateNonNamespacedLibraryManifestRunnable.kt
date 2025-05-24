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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import org.gradle.api.file.RegularFileProperty

abstract class CreateNonNamespacedLibraryManifestRunnable :
    ProfileAwareWorkAction<CreateNonNamespacedLibraryManifestRequest>() {

    override fun run() {
        NamespaceRemover.rewrite(
            parameters.originalManifestFile.asFile.get().toPath(),
            parameters.strippedManifestFile.asFile.get().toPath()
        )
    }
}

abstract class CreateNonNamespacedLibraryManifestRequest : ProfileAwareWorkAction.Parameters() {
    abstract val originalManifestFile: RegularFileProperty
    abstract val strippedManifestFile: RegularFileProperty
}