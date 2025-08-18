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

@file:JvmName("AutoNamespaceLocation")
package com.android.build.gradle.internal.res.namespaced

import com.android.utils.FileUtils
import org.gradle.api.artifacts.component.ComponentIdentifier


fun getAutoNamespacedLibraryFileName(artifactId: ComponentIdentifier): String {
    // TODO: handle collisions!
    return "${FileUtils.sanitizeFileName(artifactId.displayName)}.apk"
}
