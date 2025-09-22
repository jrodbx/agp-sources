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

package com.android.build.gradle.internal.services

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption.LINT_HEAP_SIZE
import com.android.build.gradle.options.StringOption.LINT_RESERVED_MEMORY_PER_TASK
import com.sun.management.OperatingSystemMXBean
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * [LintParallelBuildService] limits the number of lint workers to avoid running out of memory.
 */
abstract class LintParallelBuildService : BuildService<BuildServiceParameters.None> {

    companion object {
        fun calculateMaxParallelUsages(
            projectOptions: ProjectOptions,
            maxRuntimeMemory: Long,
            totalPhysicalMemory: Long?
        ): Int? {
            return if (projectOptions.get(BooleanOption.RUN_LINT_IN_PROCESS)) {
                calculateMaxParallelUsagesInProcess(projectOptions, maxRuntimeMemory)
            } else {
                calculateMaxParallelUsagesOutOfProcess(
                    projectOptions,
                    maxRuntimeMemory,
                    totalPhysicalMemory ?: return null
                )
            }
        }

        private fun calculateMaxParallelUsagesInProcess(
            projectOptions: ProjectOptions,
            maxRuntimeMemory: Long
        ): Int {
            // We reserve 512 megabytes per lint task, unless the user specifies a different value.
            val memoryPerLintTask: Long =
                projectOptions.get(LINT_RESERVED_MEMORY_PER_TASK)?.let {
                    parseMemorySize(it, LINT_RESERVED_MEMORY_PER_TASK.propertyName)
                } ?: (512 * 1024 * 1024)
            // Multiply maxRuntimeMemory by 0.75 to save memory for other things too
            val maxLintMemory = (maxRuntimeMemory * 0.75).toLong()
            return Math.floorDiv(maxLintMemory, memoryPerLintTask).coerceAtLeast(1).toInt()
        }

        private fun calculateMaxParallelUsagesOutOfProcess(
            projectOptions: ProjectOptions,
            maxRuntimeMemory: Long,
            totalPhysicalMemory: Long
        ): Int {
            val maxParallelUsage =
                Math.floorDiv(
                    totalPhysicalMemory,
                    parseMemorySize(
                        calculateLintHeapSize(projectOptions.get(LINT_HEAP_SIZE), maxRuntimeMemory),
                        LINT_HEAP_SIZE.propertyName
                    )
                )
            // Leave space for at least the gradle daemon and the kotlin daemon
            return (maxParallelUsage - 2).coerceAtLeast(1).toInt()
        }

        // Default to using the main Gradle daemon heap size if no lint heap size is specified
        fun calculateLintHeapSize(
            userSpecifiedLintHeapSize: String?,
            maxRuntimeMemory: Long
        ): String = userSpecifiedLintHeapSize ?: "${maxRuntimeMemory / 1024 / 1024}m"

        /**
         * Parses a memory size (e.g., max heap) string (e.g., "512m" or "2g"), and returns the
         * corresponding number of bytes as a Long.
         */
        private fun parseMemorySize(memorySize: String, propertyName: String): Long {
            val value = memorySize.lowercase(Locale.US)
            val longOrNull = when {
                value.toLongOrNull() != null -> value.toLongOrNull()
                value.endsWith("k") -> {
                    value.substring(0, value.length-1).toLongOrNull()?.let { it * 1024 }
                }
                value.endsWith("m") -> {
                    value.substring(0, value.length-1).toLongOrNull()?.let { it * 1024 * 1024 }
                }
                value.endsWith("g") -> {
                    value.substring(0, value.length-1)
                        .toLongOrNull()
                        ?.let { it * 1024 * 1024 * 1024 }
                }
                else ->  {
                    null
                }
            }
            return longOrNull
                ?: throw RuntimeException("Failed to parse $propertyName \"$memorySize\".")
        }
    }
}

/**
 * Returns a Provider of the [LintParallelBuildService].
 *
 * Use this function instead of [getBuildService] to get the [LintParallelBuildService] because we
 * don't want an instance of the [LintParallelBuildService] per class loader.
 *
 * This function uses registerIfAbsent in order to ensure locking when accessing build services.
 */
fun BuildServiceRegistry.getLintParallelBuildService(projectOptions: ProjectOptions) =
    registerIfAbsent("LintParallelBuildService", LintParallelBuildService::class.java) { spec ->
        spec.maxParallelUsages
            .set(
                LintParallelBuildService.calculateMaxParallelUsages(
                    projectOptions,
                    Runtime.getRuntime().maxMemory(),
                    getTotalPhysicalMemory()
                )
            )
    }

private fun getTotalPhysicalMemory() = try {
    (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
} catch (t: Throwable) {
    Logging.getLogger(LintParallelBuildService::class.java)
        .info("Failed to read total available memory", t)
    null
}
