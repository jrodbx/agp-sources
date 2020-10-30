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
package com.android.builder.dexing

import com.android.dex.Dex
import com.android.dex.DexFormat
import com.android.dex.FieldId
import com.android.dex.MethodId
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import java.util.HashSet

/**
 * Dex merging strategy that tracks field and method references that can be merged. This will
 * account for duplicate references from different DEX files, and will count those as a single
 * reference.
 */
class ReferenceCountMergingStrategy : DexMergingStrategy {
    private val fieldRefs = Sets.newHashSet<FieldEvaluated>()
    private val methodRefs = Sets.newHashSet<MethodEvaluated>()
    private val currentDexes = Lists.newArrayList<Dex>()

    override fun tryToAddForMerging(dexFile: Dex): Boolean {
        return if (tryAddFields(dexFile) && tryAddMethods(dexFile)) {
            currentDexes.add(dexFile)
            true
        } else {
            false
        }
    }

    override fun startNewDex() {
        fieldRefs.clear()
        methodRefs.clear()
        currentDexes.clear()
    }

    override fun getAllDexToMerge(): ImmutableList<Dex> = ImmutableList.copyOf(currentDexes)

    private fun tryAddFields(dexFile: Dex): Boolean {
        val fieldIds = dexFile.fieldIds()
        val fieldsEvaluated = HashSet<FieldEvaluated>(fieldIds.size)
        fieldIds.forEach { f -> fieldsEvaluated.add(FieldEvaluated.fromDex(f, dexFile)) }
        // find how many references are shared, and deduct from the total count
        val shared = Sets.intersection(fieldsEvaluated, fieldRefs).size
        return if (fieldRefs.size + fieldsEvaluated.size - shared > DexFormat.MAX_MEMBER_IDX + 1) {
            false
        } else {
            fieldRefs.addAll(fieldsEvaluated)
            true
        }
    }

    private fun tryAddMethods(dexFile: Dex): Boolean {
        val methodIds = dexFile.methodIds()
        val methodsEvaluated = HashSet<MethodEvaluated>(methodIds.size)
        methodIds.forEach { f -> methodsEvaluated.add(MethodEvaluated.fromDex(f, dexFile)) }
        // find how many references are shared, and deduct from the total count
        val shared = Sets.intersection(methodsEvaluated, methodRefs).size
        return if (methodRefs.size + methodsEvaluated.size - shared > DexFormat.MAX_MEMBER_IDX + 1) {
            false
        } else {
            methodRefs.addAll(methodsEvaluated)
            true
        }
    }

    private data class FieldEvaluated(
            val declaringClass: String,
            val type: String,
            val name: String
    ) {
        companion object {
            fun fromDex(fieldId: FieldId, dex: Dex): FieldEvaluated {
                return FieldEvaluated(
                        dex.typeNames()[fieldId.declaringClassIndex],
                        dex.typeNames()[fieldId.typeIndex],
                        dex.strings()[fieldId.nameIndex])
            }
        }
    }

    private data class MethodEvaluated(
            val declaringClass: String,
            val name: String,
            val protoShorty: String,
            val protoReturnType: String,
            val protoParameterTypes: String
    ) {
        companion object {
            fun fromDex(methodId: MethodId, dex: Dex): MethodEvaluated {
                val protoId = dex.protoIds()[methodId.protoIndex]
                return MethodEvaluated(
                        dex.typeNames()[methodId.declaringClassIndex],
                        dex.strings()[methodId.nameIndex],
                        dex.strings()[protoId.shortyIndex],
                        dex.typeNames()[protoId.returnTypeIndex],
                        dex.readTypeList(protoId.parametersOffset).toString()
                )
            }
        }
    }
}
