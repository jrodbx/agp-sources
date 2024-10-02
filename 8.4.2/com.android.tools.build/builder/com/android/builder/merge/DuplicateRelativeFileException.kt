/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.merge

import com.android.builder.utils.agpReferenceDocsUrl

/**
 * Exception by [StreamMergeAlgorithms.acceptOnlyOne] if more than one file needs to be
 * merged.
 */
class DuplicateRelativeFileException constructor(
    private val path: String,
    private val size: Int,
    private val inputs: List<String>,
    cause : DuplicateRelativeFileException?
) : RuntimeException(cause) {

    override val message: String
        get() {
            return StringBuilder().apply {
                append(size).append(" files found with path '").append(path).append("'")
                if (inputs.isEmpty()) {
                    append(".\n")
                } else {
                    append(" from inputs:\n")
                    for (input in inputs) {
                        append(" - ").append(input).append("\n")
                    }
                }
                if (path.endsWith(".so")) {
                    append(
                        "If you are using jniLibs and CMake IMPORTED targets, see\n" +
                                "https://developer.android.com/r/tools/jniLibs-vs-imported-targets"
                    )
                } else {
                    append(
                        "Adding a packaging block may help, please refer to\n" +
                                "${agpReferenceDocsUrl("com/android/build/api/dsl/Packaging")}\n" +
                                "for more information"
                    )
                }
            }.toString()
        }

    constructor(path: String, size: Int) : this(path, size, listOf(), null)

    constructor(
        inputs: List<IncrementalFileMergerInput>,
        cause: DuplicateRelativeFileException
    ) : this(cause.path, cause.size, inputs.map { it.name }, cause)

}
