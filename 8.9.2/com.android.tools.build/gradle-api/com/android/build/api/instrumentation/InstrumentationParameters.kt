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

import org.gradle.api.model.ObjectFactory
import java.io.Serializable

/**
 * Parameters for a registered [AsmClassVisitorFactory].
 *
 * Build authors should extend this interface with any additional inputs needed for their ASM
 * visitor, or use [InstrumentationParameters.None] if no parameters are needed.
 *
 * The parameters will be instantiated using [ObjectFactory.newInstance], configured using the
 * given config when registering the visitor, and injected to the factory on instantiation.
 *
 * The parameters will be used as Gradle inputs. Make sure to declare the inputs by annotating them
 * using Gradle's input annotations so that it's compatible with Gradle's up-to-date checks.
 *
 * Example:
 * ```
 *  interface ParametersImpl : InstrumentationParameters {
 *    @get:Input
 *    val intValue: Property<Int>
 *
 *    @get:Internal
 *    val listOfStrings: ListProperty<String>
 *  }
 *
 *  androidComponents {
 *      onVariants(selector().all(), {
 *          instrumentation.transformClassesWith(AsmClassVisitorFactoryImpl.class,
 *                                               InstrumentationScope.Project) { params ->
 *               // parameters configuration
 *               params.intValue.set(1)
 *               params.listOfStrings.set(listOf("a", "b"))
 *          }
 *      })
 *  }
 * ```
 */
interface InstrumentationParameters : Serializable
{
    class None : InstrumentationParameters
}
