/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("ArtifactTypeUtils")
package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactType
import com.android.build.api.artifact.PublicArtifactType
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.InternalArtifactType
import org.gradle.api.file.Directory
import java.lang.RuntimeException
import kotlin.reflect.KClass

/**
 * Utility class for [ArtifactType]
 */

private val publicArtifactMap : Map<String, KClass<out ArtifactType<*>>> =
        PublicArtifactType::class.sealedSubclasses.associateBy {
                it.objectInstance?.name() ?: throw RuntimeException("No instance")
        }

private val sourceArtifactMap : Map<String, KClass<out ArtifactType<*>>> =
        SourceArtifactType::class.sealedSubclasses.associateBy {
                it.objectInstance?.name() ?: throw RuntimeException("No instance")
        }
private val buildArtifactMap : Map<String, KClass<out ArtifactType<*>>> =
        BuildArtifactType::class.sealedSubclasses.associateBy {
                it.objectInstance?.name() ?: throw RuntimeException("No instance")
        }
private val internalArtifactMap : Map<String, KClass<out ArtifactType<*>>> =
        InternalArtifactType::class.sealedSubclasses.associateBy {
                it.objectInstance?.name() ?: throw RuntimeException("No instance")
        }
private val anchorArtifactMap : Map<String, KClass<out ArtifactType<*>>> =
        AnchorOutputType::class.sealedSubclasses.associateBy {
                it.objectInstance?.name() ?: throw RuntimeException("No instance")
        }

/**
 * Return the enum of [ArtifactType] base on the name.
 *
 * The typical implementation of valueOf in an enum class cannot be used because there are
 * multiple implementations of [ArtifactType].  For this to work, the name of all
 * [ArtifactType] must be unique across all implementations.
 */
fun String.toArtifactType() : ArtifactType<*> =
    publicArtifactMap[this]?.objectInstance ?:
            sourceArtifactMap[this]?.objectInstance ?:
            buildArtifactMap[this]?.objectInstance  ?:
            internalArtifactMap[this]?.objectInstance ?:
            anchorArtifactMap[this]?.objectInstance ?:
            throw IllegalArgumentException("'$this' is not a value ArtifactType.")


