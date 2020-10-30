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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.lang.RuntimeException

class StorageProviderImpl {

    fun lock() {
        fileStorage.lock()
        directory.lock()
    }

    private val fileStorage = TypedStorageProvider<RegularFile> {
        objectFactory -> objectFactory.fileProperty()
    }
    private val directory= TypedStorageProvider<Directory> {
        objectFactory -> objectFactory.directoryProperty()
    }

    fun <T: FileSystemLocation> getStorage(artifactKind: ArtifactKind<T>): TypedStorageProvider<T> {
        @Suppress("Unchecked_cast")
        return when(artifactKind) {
            ArtifactKind.FILE -> fileStorage
            ArtifactKind.DIRECTORY -> directory
            else -> throw RuntimeException("Cannot handle $this")
        } as TypedStorageProvider<T>
    }
}

class TypedStorageProvider<T :FileSystemLocation>(private val propertyAllocator: (ObjectFactory) -> Property<T>) {
    private val singleStorage= mutableMapOf<ArtifactType.Single,  SingleArtifactContainer<T>>()
    private val multipleStorage=  mutableMapOf<ArtifactType.Multiple,  MultipleArtifactContainer<T>>()

    @Synchronized
    internal fun <ARTIFACT_TYPE> getArtifact(objects: ObjectFactory, artifactType: ARTIFACT_TYPE): SingleArtifactContainer<T> where
        ARTIFACT_TYPE: ArtifactType.Single,
        ARTIFACT_TYPE: ArtifactType<T> {

        return singleStorage.getOrPut(artifactType) {
            SingleArtifactContainer<T> {
                SinglePropertyAdapter(propertyAllocator(objects))
            }
        }
    }

    internal fun <ARTIFACT_TYPE> getArtifact(objects: ObjectFactory, artifactType: ARTIFACT_TYPE): MultipleArtifactContainer<T> where
            ARTIFACT_TYPE: ArtifactType.Multiple,
            ARTIFACT_TYPE: ArtifactType<T> {

        return multipleStorage.getOrPut(artifactType) {
            MultipleArtifactContainer<T> {
                MultiplePropertyAdapter(
                    objects.listProperty(artifactType.kind.dataType().java))
            }
        }
    }

    internal fun <ARTIFACT_TYPE> copy(type: ARTIFACT_TYPE,
        container: SingleArtifactContainer<T>)
        where ARTIFACT_TYPE: ArtifactType<T>,
              ARTIFACT_TYPE: ArtifactType.Single {

       singleStorage[type] = container
    }

    fun lock() {
        singleStorage.values.forEach { it.disallowChanges() }
        multipleStorage.values.forEach { it.disallowChanges() }
    }
}