/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.gradle.internal.services

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * During configuration time, creating a file or directory could lead to configuration cache miss
 * in the following build because Gradle inspects the return value of some File operations(e.g.
 * [File.exists]) between builds and treats them as configuration input. For example, if a file is
 * created in the first run and it already exists before the second run, calling [File.exists] to
 * that file at configuration time would give us different value which means configuration cache
 * is not reusable from Gradle point of view.
 *
 * There are some cases where we have to create a file or directory during configuration time and
 * we know whether that file/directory exist or not doesn't affect configuration. In such cases,
 * this class helps us create a file or directory during configuration time without causing
 * configuration cache miss.
 *
 * To create a file/directory during configuration time: create them in [obtain] function either
 * using the path declared in [Params] or computing the path directly. The return value of [obtain]
 * function needs to be same between different runs unless you want to config cache to be invalided.
 * It can be a constant like [IGNORE_FILE_CREATION] if you don't need this function to return
 * anything(Note [Unit] won't work). It can also be a [File] or other types.
 *
 */
interface ConfigPhaseFileCreator<T, P : ConfigPhaseFileCreator.Params> : ValueSource<T, P> {

    interface Params : ValueSourceParameters
}

const val IGNORE_FILE_CREATION = ""
