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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.PropertiesValueSource
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.io.StringReader
import java.util.Properties

/**
 * Retrieve the project local properties if they are available.
 * If there is no local properties file then an empty set of properties is returned.
 */
fun gradleLocalProperties(projectRootDir : File, providers: ProviderFactory) : Properties {
    val properties = Properties()
    val propertiesContent =
        providers.of(PropertiesValueSource::class.java) {
            it.parameters.projectRoot.set(projectRootDir)
        }.get()

    StringReader(propertiesContent).use { reader ->
        properties.load(reader)
    }

    return properties
}

