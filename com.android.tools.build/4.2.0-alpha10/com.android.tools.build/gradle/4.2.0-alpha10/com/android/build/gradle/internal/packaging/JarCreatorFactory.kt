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

package com.android.build.gradle.internal.packaging

import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarFlinger
import com.android.builder.packaging.JarMerger
import java.nio.file.Path
import java.util.function.Predicate

object JarCreatorFactory {

    fun make(
        jarFile: Path,
        filter: Predicate<String>? = null,
        type: JarCreatorType = JarCreatorType.JAR_FLINGER
    ): JarCreator {
        return when (type) {
            JarCreatorType.JAR_MERGER -> JarMerger(jarFile, filter)
            JarCreatorType.JAR_FLINGER -> JarFlinger(jarFile, filter)
        }
    }
}

enum class JarCreatorType {
    JAR_MERGER,
    JAR_FLINGER,
}
