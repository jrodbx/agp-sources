/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvm
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidTestOnJvmImpl @Inject constructor(
    val dslServices: DslServices,
): KotlinMultiplatformAndroidTestOnJvm {
    // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
    private val testTasks = dslServices.domainObjectSet(Test::class.java)

    override var isReturnDefaultValues: Boolean = false
    override var isIncludeAndroidResources: Boolean = false
    override var enableCoverage: Boolean = false

    override fun all(configAction: (Test) -> Unit) {
        testTasks.all(configAction)
    }

    fun all(configAction: Action<Test>) {
        testTasks.all(configAction)
    }

    /**
     * Configures a given test task. The configuration closures that were passed to {@link
     * #all(Closure)} will be applied to it.
     *
     * <p>Not meant to be called from build scripts. The reason it exists is that tasks are
     * created after the build scripts are evaluated, so users have to "register" their
     * configuration closures first and we can only apply them later.
     *
     * @since 1.2.0
     */
    fun applyConfiguration(task: Test) {
        testTasks.add(task)
    }
}
