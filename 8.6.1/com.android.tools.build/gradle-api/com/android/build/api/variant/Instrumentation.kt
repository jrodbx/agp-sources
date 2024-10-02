/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.provider.SetProperty

/**
 * Options to register asm class visitors and to configure the instrumentation process.
 */
interface Instrumentation {

    /**
     * Registers an asm class visitor to instrument the classes defined by the given scope.
     * An instance of the factory will be instantiated and used to create visitors for each class.
     *
     * Example:
     *
     * ```
     *  androidComponents {
     *      onVariants(selector().all(), {
     *          instrumentation.transformClassesWith(AsmClassVisitorFactoryImpl.class,
     *                                               InstrumentationScope.Project) { params ->
     *              params.x = "value"
     *          }
     *          instrumentation.setAsmFramesComputationMode(COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
     *      })
     *  }
     * ```
     *
     * @param classVisitorFactoryImplClass the factory class implementing [AsmClassVisitorFactory]
     * @param scope either instrumenting the classes of the current project or the project and its
     * dependencies
     * @param instrumentationParamsConfig the configuration function to be applied to the
     * instantiated [InstrumentationParameters] object before passed to
     * [AsmClassVisitorFactory.createClassVisitor].
     */
    fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    )

    /**
     * Sets the frame computation mode that will be applied to the bytecode of the classes
     * instrumented by ASM visitors registered through [transformClassesWith]. The default mode is
     * to [copy frames][FramesComputationMode.COPY_FRAMES].
     *
     * When setting this multiple times, the mode with the highest enum value will be selected.
     */
    fun setAsmFramesComputationMode(mode: FramesComputationMode)

    /**
     * The set of glob patterns to exclude from instrumentation. Classes matching any of these
     * patterns in the project sources and dependencies jars do not get instrumented by any
     * [AsmClassVisitorFactory] registered via [transformClassesWith].
     *
     * Do not add the file extension to the end as the filtration is done on compiled classes and
     * the `.class` suffix is not included in the pattern matching.
     *
     * Example usage:
     *
     * ```
     *  androidComponents {
     *      onVariants(selector().all(), {
     *          instrumentation.excludes.add("com`/`example`/`donotinstrument`/`**")
     *          instrumentation.excludes.add("**`/`*Test")
     *      })
     *  }
     * ```
     */
    val excludes: SetProperty<String>
}
