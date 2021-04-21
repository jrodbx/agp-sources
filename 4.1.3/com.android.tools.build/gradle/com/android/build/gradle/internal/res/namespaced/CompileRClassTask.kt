/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_SOURCES
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Task to compile a directory containing R.java file(s) and jar the result.
 *
 * For namespaced libraries, there will be exactly one R.java file, but for applications there will
 * be a regenerated one per dependency.
 *
 * In the future, this might not call javac at all, but it needs to be profiled first.
 */
class CompileRClassTaskCreationAction(private val component: ComponentPropertiesImpl) :
    TaskCreationAction<JavaCompile>() {

    private val output = component.globalScope.project.objects.directoryProperty()

    override val name: String
        get() = component.computeTaskName("compile", "FinalRClass")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<JavaCompile>) {
        super.handleProvider(taskProvider)

        component.artifacts.setInitialProvider(
            taskProvider
        ) {  output  }.withName(SdkConstants.FD_RES).on(RUNTIME_R_CLASS_CLASSES)
    }

    override fun configure(task: JavaCompile) {
        val taskContainer: MutableTaskContainer = component.taskContainer
        task.dependsOn(taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, component.name)

        task.classpath = task.project.files()
        if (component.variantType.isTestComponent || component.variantType.isApk) {
            task.source(component.artifacts.get(RUNTIME_R_CLASS_SOURCES))
        }
        task.setDestinationDir(output.asFile)

        // manually declare our output directory as a Task output since it's not annotated as
        // an OutputDirectory on the task implementation.
        task.outputs.dir(output)
    }
}