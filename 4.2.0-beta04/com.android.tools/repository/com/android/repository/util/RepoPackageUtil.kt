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
package com.android.repository.util

import com.android.repository.api.RepoPackage

fun getRepoPackagePrefix(pathOrPrefix: String): String = pathOrPrefix.substringBeforeLast(RepoPackage.PATH_SEPARATOR)

fun getAllRepoPackagePrefixes(path: String): List<String> = generateSequence(path) {
  it.substringBeforeLast(RepoPackage.PATH_SEPARATOR, "").takeIf { it.isNotEmpty() }
}.toList()
