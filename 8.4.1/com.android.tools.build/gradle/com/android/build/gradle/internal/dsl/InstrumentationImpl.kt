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

package com.android.build.gradle.internal.dsl

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Instrumentation
import com.android.build.gradle.internal.instrumentation.AsmClassVisitorsFactoryRegistry
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty

class InstrumentationImpl(
    services: TaskCreationServices,
    variantServices: VariantServices,
    private val isLibraryVariant: Boolean
) : Instrumentation {

    private val asmClassVisitorsRegistry = AsmClassVisitorsFactoryRegistry(services.issueReporter)

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        if (isLibraryVariant && scope == InstrumentationScope.ALL) {
            throw RuntimeException(
                "Can't register ${classVisitorFactoryImplClass.name} to " +
                        "instrument library dependencies.\n" +
                        "Instrumenting library dependencies will have no effect on library " +
                        "consumers, move the dependencies instrumentation to be done in the " +
                        "consuming app or test component."
            )
        }
        asmClassVisitorsRegistry.register(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        asmClassVisitorsRegistry.setAsmFramesComputationMode(mode)
    }

    override val excludes: SetProperty<String> =
        variantServices.setPropertyOf(String::class.java, mutableListOf())

    // private APIs

    val registeredProjectClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = asmClassVisitorsRegistry.projectClassesVisitors.map { it.visitorFactory }

    val registeredDependenciesClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = asmClassVisitorsRegistry.dependenciesClassesVisitors.map { it.visitorFactory }

    val finalAsmFramesComputationMode: FramesComputationMode
        get() = asmClassVisitorsRegistry.framesComputationMode

    fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory, asmApiVersion: Int) {
        asmClassVisitorsRegistry.configureAndLock(objectFactory, asmApiVersion)
    }
}
