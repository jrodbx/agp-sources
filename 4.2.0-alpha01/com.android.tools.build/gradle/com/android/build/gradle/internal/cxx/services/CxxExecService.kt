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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.scope.GlobalScope
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec

/**
 * Private service key for [GradleExecServices].
 */
private val EXEC_SERVICE_KEY = object : CxxServiceKey<GradleExecServices> {
    override val type = GradleExecServices::class.java
}

/**
 * Private convenience tuple class to hold exec and javaexec provided
 * by Gradle runtime.
 * - exec is for creating native processes
 * - javaexec is for creating JVM processes
 *
 * These are supplied by Gradle and should be used instead of built-in
 * Java methods of starting a process. Presumably, this allows Gradle
 * to intercept the stderr and stdout for its own purposes. Gradle
 * probably does other things with this abstraction as well.
 *
 * These services are held by Gradle at the [org.gradle.api.Project]
 * so they are accessed from the equivalent Cxx model level which is
 * [CxxProjectModel]. Static functions wouldn't work because they are
 * provided by an instance of [org.gradle.api.Project].
 *
 * The reason for using a service like this instead of just passing
 * these functions around through method calls is separate the concern
 * of starting processes from the actual concern of those functions.
 */
private class GradleExecServices(
    val exec: (Action<in ExecSpec?>) -> ExecResult,
    val javaexec: (Action<in JavaExecSpec?>) -> ExecResult
)

/**
 * Get Gradle's native process exec service.
 */
val CxxProjectModel.exec get() = services[EXEC_SERVICE_KEY].exec

/**
 * Get Gradle's Java process exec service.
 */
val CxxProjectModel.javaexec get() = services[EXEC_SERVICE_KEY].javaexec

/**
 * Create the process exec service and register it into [services].
 * Creation of [GradleExecServices] is deferred until use.
 */
internal fun createExecService(global: GlobalScope, services: CxxServiceRegistryBuilder) {
    services.registerFactory(EXEC_SERVICE_KEY) {
        GradleExecServices(
            global.project::exec,
            global.project::javaexec)
    }
}