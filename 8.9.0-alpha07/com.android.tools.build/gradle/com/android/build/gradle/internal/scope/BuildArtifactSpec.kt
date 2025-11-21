/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.api.artifact.SourceArtifactType

/**
 * Specification to define supported features of [BuildArtifactType]
 */
data class BuildArtifactSpec(
        val type : Artifact<*>,
        val appendable : Boolean,
        val replaceable: Boolean) {
    companion object {
        private val specMap = mapOf(
                //   type                                      appendable           replaceable
            spec(BuildArtifactType.JAVAC_CLASSES,          true,       true),
            spec(BuildArtifactType.JAVA_COMPILE_CLASSPATH, true,       false),
            spec(SourceArtifactType.ANDROID_RESOURCES,     true,       true),
            spec(InternalArtifactType.BASE_MODULE_METADATA,false,      true)
        )

        fun spec(type : Artifact<*>, appendable : Boolean, replaceable: Boolean) =
            type to BuildArtifactSpec(
                type,
                appendable,
                replaceable
            )

        fun get(type : Artifact<*>) =
                specMap[type]
                        ?: throw RuntimeException("Specification is not defined for type '$type'.")

        fun has(type : Artifact<*>) = specMap.containsKey(type)
    }
}
