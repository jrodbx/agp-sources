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

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.objectweb.asm.ClassVisitor
import java.io.Serializable

/**
 * A factory to create class visitor objects to instrument classes.
 *
 * The implementation of this interface must be an abstract class where the [parameters] and
 * [instrumentationContext] are left unimplemented. The class must have an empty constructor which
 * will be used to construct the factory object.
 */
interface AsmClassVisitorFactory<ParametersT : InstrumentationParameters> : Serializable {

    /**
     * The parameters that will be instantiated, configured using the given config when registering
     * the visitor, and injected on instantiation.
     *
     * This field must be left unimplemented.
     */
    @get:Nested
    val parameters: Property<ParametersT>

    /**
     * Contains parameters to help instantiate the visitor objects.
     *
     * This field must be left unimplemented.
     */
    @get:Nested
    val instrumentationContext: InstrumentationContext

    /**
     * Creates a class visitor object that will visit a class with the given [classContext]. The
     * returned class visitor must delegate its calls to [nextClassVisitor]. If at this point the
     * factory is not interested in instrumenting the class with given [classContext], then return
     * [nextClassVisitor].
     *
     * The given [classContext] contains static information about the classes before starting the
     * instrumentation process. Any changes in interfaces or superclasses for the class with the
     * given [classContext] or for any other class in its classpath by a previous visitor will
     * not be reflected in the [classContext] object.
     *
     * [classContext] can also be used to get the data for classes that are in the runtime classpath
     * of the class being visited.
     *
     * This method must handle asynchronous calls.
     *
     * @param classContext contains information about the class that will be instrumented by the
     *                     returned class visitor.
     * @param nextClassVisitor the [ClassVisitor] to which the created [ClassVisitor] must delegate
     *                         method calls.
     */
    fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor

    /**
     * Whether the factory wants to instrument the class with the given [classData].
     *
     * If returned true, [createClassVisitor] will be called and the returned class visitor will
     * visit the class.
     *
     * This method must handle asynchronous calls.
     */
    fun isInstrumentable(classData: ClassData): Boolean
}
