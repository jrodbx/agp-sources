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
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.builder.errors.IssueReporter
import org.gradle.api.model.ObjectFactory

class AsmClassVisitorsFactoryRegistry(private val issueReporter: IssueReporter) {
    private var isLocked = false
    var framesComputationMode = FramesComputationMode.COPY_FRAMES

    val projectClassesVisitors =
        ArrayList<AsmClassVisitorFactoryEntry<out InstrumentationParameters>>()
    val dependenciesClassesVisitors =
        ArrayList<AsmClassVisitorFactoryEntry<out InstrumentationParameters>>()

    fun <ParamT : InstrumentationParameters> register(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        if (isLocked) {
            issueReporter.reportError(
                IssueReporter.Type.EDIT_LOCKED_DSL_VALUE,
                "It is too late to register the class visitor factory " +
                        "${classVisitorFactoryImplClass.name}, " +
                        "The DSL is now locked as the variants have been created.\n"
            )
            return
        }
        val visitorEntry = AsmClassVisitorFactoryEntry(
            classVisitorFactoryImplClass,
            instrumentationParamsConfig
        )
        if (scope == InstrumentationScope.ALL) {
            dependenciesClassesVisitors.add(visitorEntry)
        }
        projectClassesVisitors.add(visitorEntry)
    }

    fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        if (isLocked) {
            issueReporter.reportError(
                IssueReporter.Type.EDIT_LOCKED_DSL_VALUE,
                "It is too late to set the asm frames computation mode, " +
                        "The DSL is now locked as the variants have been created.\n"
            )
            return
        }
        if (mode.ordinal > framesComputationMode.ordinal) {
            framesComputationMode = mode
        }
    }

    fun configureAndLock(objectFactory: ObjectFactory, asmApiVersion: Int) {
        isLocked = true
        projectClassesVisitors.forEach {
            it.configure(
                objectFactory,
                asmApiVersion
            )
        }
    }
}