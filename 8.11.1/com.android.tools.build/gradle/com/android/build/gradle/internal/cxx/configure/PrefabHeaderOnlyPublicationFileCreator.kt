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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.PrefabHeaderOnlyPublicationFileCreator.Params
import com.android.build.gradle.internal.cxx.prefab.PrefabPublication
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.HeaderOnly
import com.android.build.gradle.internal.cxx.prefab.writePublicationFile
import com.android.build.gradle.internal.services.ConfigPhaseFileCreator
import com.android.build.gradle.internal.services.IGNORE_FILE_CREATION
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

/**
 * ValueProvider used to write the header-only Prefab publication.
 */
abstract class PrefabHeaderOnlyPublicationFileCreator : ConfigPhaseFileCreator<String, Params> {
    interface Params: ConfigPhaseFileCreator.Params {
        val publication: Property<PrefabPublication>
    }

    override fun obtain() : String {
        HeaderOnly.writePublicationFile(parameters.publication.get())
        return IGNORE_FILE_CREATION
    }
}

fun writeHeaderOnlyPublicationFile(
    providerFactory: ProviderFactory,
    publication : PrefabPublication) {
    providerFactory.of(PrefabHeaderOnlyPublicationFileCreator::class.java) {
        it.parameters.publication.set(publication)
    }.get()
}
