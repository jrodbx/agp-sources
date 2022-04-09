/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.instrumentation

import org.gradle.api.logging.Logging
import org.objectweb.asm.Type
import java.io.Closeable
import java.util.Collections
import kotlin.math.min

class InstrumentationIssueHandler: Closeable {
    private val classesNotOnClasspath = Collections.synchronizedSet(mutableSetOf<String>())
    private val logger = Logging.getLogger(this::class.java)

    fun warnAboutClassNotOnTheClasspath(classInternalName: String) {
        classesNotOnClasspath.add(classInternalName)
    }

    override fun close() {
        if (classesNotOnClasspath.isNotEmpty()) {
            val classesToReport =
                classesNotOnClasspath
                    .take(MAX_NUMBER_OF_CLASSES_TO_REPORT)
                    .map { Type.getObjectType(it).className }

            logger.warn(
                """
                    ASM Instrumentation process wasn't able to resolve some classes, this means that
                    the instrumented classes might contain corrupt stack frames. Make sure the
                    dependencies that contain these classes are on the runtime or the provided
                    classpath. Otherwise, the jvm might fail to load the corrupt classes at runtime
                    when running in a jvm environment like unit tests.

                    Classes that weren't resolved:
                    ${classesToReport.joinToString("\n                    ") { "> $it" }}
                    ${if (classesNotOnClasspath.size > MAX_NUMBER_OF_CLASSES_TO_REPORT) {
                        "-- ${classesNotOnClasspath.size - MAX_NUMBER_OF_CLASSES_TO_REPORT} more classes --\n"
                    } else ""}
                """.trimIndent()
            )
            classesNotOnClasspath.clear()
        }
    }

    companion object {
        const val MAX_NUMBER_OF_CLASSES_TO_REPORT = 10
    }
}
