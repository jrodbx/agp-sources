/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.manifest

import com.android.manifmerger.ManifestProvider
import java.io.File

/* Used to pass to the merger manifest snippet that needs to be added during merge */
class ManifestProviderImpl(private val manifest: File, private val name: String) :
        ManifestProvider {
    override fun getManifest(): File {
        return manifest
    }

    override fun getName(): String {
        return name
    }
}
