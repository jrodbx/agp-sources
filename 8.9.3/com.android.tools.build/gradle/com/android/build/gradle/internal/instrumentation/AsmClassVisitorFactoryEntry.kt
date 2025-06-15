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

package com.android.build.gradle.internal.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.model.ObjectFactory

/**
 * A class representing a registered asm class visitor factory to be used internally.
 *
 * This class handles initiating and configuring the factory object and its parameters.
 */
class AsmClassVisitorFactoryEntry<ParamT : InstrumentationParameters>(
    private val visitorFactoryClass: Class<out AsmClassVisitorFactory<ParamT>>,
    private val paramsConfig: (ParamT) -> Unit
) {
    lateinit var visitorFactory: AsmClassVisitorFactory<ParamT>
        private set

    fun configure(
        objectFactory: ObjectFactory,
        apiVersion: Int
    ) {
        // Gradle currently can't figure out the class type of the parameters and instead
        // instantiates the parameters as InstrumentationParameters type. So we handle injecting
        // the correct parameters object on our own.
        val paramsClass = getParamsImplClass(visitorFactoryClass)
        @Suppress("UNCHECKED_CAST")
        val parameters = if (paramsClass == InstrumentationParameters.None::class.java) {
            InstrumentationParameters.None()
        } else {
            objectFactory.newInstance(paramsClass)
        } as ParamT
        paramsConfig.invoke(parameters)

        visitorFactory = objectFactory.newInstance(visitorFactoryClass)
        visitorFactory.parameters.setDisallowChanges(parameters)
        visitorFactory.instrumentationContext.apiVersion.setDisallowChanges(apiVersion)
    }
}
