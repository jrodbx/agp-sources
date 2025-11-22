/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.api.dsl.AgpTestSuite
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.dsl.JUnitEngineSpec
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.testing.base.TestSuiteTarget
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Implementation of the [AgpTestSuite] Dsl extension.
 */
abstract class AgpTestSuiteImpl(
    private val name: String,
    val objects: ObjectFactory
): AgpTestSuite {

    private val jUnitEngineSpec = objects.newInstance(
        JUnitEngineSpecImpl::class.java,
    )

    private val junitEngineUsed = AtomicBoolean(false)

    fun getJunitEngineIfUsed(): JUnitEngineSpec? = jUnitEngineSpec.takeIf { junitEngineUsed.get() }

    override fun useJunitEngine(action: JUnitEngineSpec.() -> Unit) {
        action.invoke(useJunitEngine)
    }

    fun useJunitEngine(action: Action<JUnitEngineSpec>) {
        action.execute(useJunitEngine)
    }

    override val useJunitEngine: JUnitEngineSpec
        get() {
            junitEngineUsed.set(true)
            return jUnitEngineSpec
        }

    override fun getName(): String = name

    override val targetProductFlavors = mutableListOf<Pair<String, String>>()
    override val targetVariants = mutableListOf<String>()

    @Suppress("UnstableApiUsage")
    override fun getTargets(): ExtensiblePolymorphicDomainObjectContainer<out TestSuiteTarget> {
        return objects.polymorphicDomainObjectContainer(TestSuiteTarget::class.java)
    }

    override val dependencies: AgpTestSuiteDependencies = objects.newInstance(AgpTestSuiteDependencies::class.java)

    fun dependencies(action:Action<AgpTestSuiteDependencies>) {
        action.execute(dependencies)
    }

    override fun dependencies(action: AgpTestSuiteDependencies.() -> Unit) {
        action.invoke(dependencies)
    }
}
