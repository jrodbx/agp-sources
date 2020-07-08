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

@file:JvmName("R8ResourceShrinker")

package com.android.builder.dexing

import com.android.tools.r8.ProgramResource
import com.android.tools.r8.ProgramResourceProvider
import com.android.tools.r8.ResourceShrinker
import com.android.tools.r8.origin.PathOrigin
import java.io.File

fun runResourceShrinkerAnalysis(bytes: ByteArray, file: File, callback: AnalysisCallback) {
    val resource =
        ProgramResource.fromBytes(PathOrigin(file.toPath()), ProgramResource.Kind.DEX, bytes, null)
    val provider = ProgramResourceProvider { listOf(resource) }

    val command = ResourceShrinker.Builder().addProgramResourceProvider(provider).build()
    ResourceShrinker.run(command, AnalysisAdapter(callback))
}

/** An adapter so R8 API classes do not leak into other modules. */
class AnalysisAdapter(val impl: AnalysisCallback) : ResourceShrinker.ReferenceChecker {
    override fun shouldProcess(internalName: String): Boolean = impl.shouldProcess(internalName)

    override fun referencedStaticField(internalName: String, fieldName: String) =
        impl.referencedStaticField(internalName, fieldName)

    override fun referencedInt(value: Int) = impl.referencedInt(value)

    override fun referencedString(value: String) = impl.referencedString(value)

    override fun referencedMethod(
        internalName: String, methodName: String, methodDescriptor: String
    ) = impl.referencedMethod(internalName, methodName, methodDescriptor)
}

interface AnalysisCallback {

    fun shouldProcess(internalName: String): Boolean

    fun referencedInt(value: Int)

    fun referencedString(value: String)

    fun referencedStaticField(internalName: String, fieldName: String)

    fun referencedMethod(internalName: String, methodName: String, methodDescriptor: String)
}