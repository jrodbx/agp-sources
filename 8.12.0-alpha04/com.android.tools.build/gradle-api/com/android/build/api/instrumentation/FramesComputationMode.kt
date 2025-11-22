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

package com.android.build.api.instrumentation

import com.android.build.api.variant.Component
import org.objectweb.asm.ClassWriter

/**
 * Indicates the frame computation mode that will be applied to the bytecode of the classes
 * instrumented by ASM visitors registered through [Component.transformClassesWith].
 *
 * The default mode is to [copy frames][FramesComputationMode.COPY_FRAMES].
 */
enum class FramesComputationMode {
    /**
     * Stack frames and the maximum stack sizes will be copied from the original classes to the
     * instrumented ones, i.e. frames and maxs will be read and visited by the class visitors and
     * then to the [ClassWriter] where they will be written without modification.
     *
     * This is the fastest mode as it doesn't require computing frames which can be
     * an expensive operation.
     *
     * Use this mode if your instrumentation process doesn't require recomputing
     * frames or maxs.
     *
     * For example, the instrumentation process could be
     * * Adding an annotation to a method
     * * Adding an interface to a class
     * * Injecting code and also manually calculating and visiting the frames and maxs for the
     *   injected code.
     */
    COPY_FRAMES,

    /**
     * Stack frames and the maximum stack size will be computed by [ClassWriter] for any modified
     * or added methods based on the classpath of the original classes. This means that neither the
     * maximum stack size nor the stack frames will be computed for methods that are copied as is
     * in the new class.
     *
     * Use this mode if your instrumentation process requires recomputing frames and/or maxs only
     * for the modified methods in each class.
     *
     * For example, the instrumentation process could be
     * * Injecting code without visiting the frames and/or maxs for the injected code.
     */
    COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,

    /**
     * Stack frames and the maximum stack sizes will be skipped when reading the original classes,
     * and will be instead computed from scratch by [ClassWriter] based on the classpath of the
     * original classes.
     *
     * Use this mode if your instrumentation process requires recomputing frames and/or maxs for all
     * instrumented classes, and for any two classes A and B, the lowest common superclass will be
     * the same before and after instrumentation.
     *
     * For example, the instrumentation process could be
     * * Injecting code without visiting the frames and/or maxs for the injected code.
     */
    COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES,

    /**
     * Stack frames and the maximum stack sizes will be skipped when reading the original classes,
     * and will **not** be computed by [ClassWriter]. Instead, when the whole instrumentation
     * process is finished for all the classes, there will be another pass where the frames are
     * computed for all classes based on the classpath of the instrumented classes. This means that
     * the frames will be computed for all classes non-incrementally regardless of which classes
     * changed or which classes were instrumented.
     *
     * Using this mode will have a large impact on the build speed, use only when it's absolutely
     * necessary.
     *
     * Use this mode if your instrumentation process requires recomputing frames and/or maxs, and
     * there will be two classes A and B, where the lowest common superclass will **not** be the
     * same before and after instrumentation.
     *
     * For example, the instrumentation process could be
     * * Changing the superclass of a subset of classes, that will result in changing the lowest
     *   common superclass of at least two classes.
     */
    COMPUTE_FRAMES_FOR_ALL_CLASSES
}
