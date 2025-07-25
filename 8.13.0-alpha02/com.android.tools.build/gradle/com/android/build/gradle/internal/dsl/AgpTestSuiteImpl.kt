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
import com.android.build.api.dsl.AgpTestSuiteTarget
import com.android.build.api.dsl.JUnitEngineSpec
import com.android.build.api.dsl.TestSuiteAssetsSpec
import com.android.build.api.dsl.TestSuiteHostJarSpec
import com.android.build.api.dsl.TestSuiteTestApkSpec
import com.android.build.api.dsl.TestTaskContext
import com.android.build.gradle.internal.testsuites.TestSuiteSourceCreationConfig
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.testing.Test
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

    override val targetVariants = mutableListOf<String>()

    private val targets = objects.domainObjectContainer(
        AgpTestSuiteTarget::class.java
    ) { name ->
        AgpTestSuiteTargetImpl(
            this@AgpTestSuiteImpl, name
        )
    }

    override fun getTargets(): NamedDomainObjectContainer<AgpTestSuiteTarget> = targets

    override fun assets(action: TestSuiteAssetsSpec.() -> Unit) {
        addSource<TestSuiteAssetsSpecImpl>(action)
    }

    override fun hostJar(action: TestSuiteHostJarSpec.() -> Unit) {
        addSource<TestSuiteHostJarSpecImpl>(action)
    }

    fun hostJar(action: Action<TestSuiteHostJarSpec>) {
        hostJar { action.execute(this) }
    }

    override fun testApk(action: TestSuiteTestApkSpec.() -> Unit) {
        throw RuntimeException("Not yet implemented")
    }

    fun testApk(action: Action<TestSuiteTestApkSpec>) {
        testApk { action.execute(this) }
    }

    private val sources = mutableListOf<TestSuiteSourceCreationConfig>()

    internal fun  getSourceContainers(): Collection<TestSuiteSourceCreationConfig> =
        sources

    override fun configureTestTasks(action: Test.(TestTaskContext) -> Unit) {
        testTaskConfigActions.add(action)
    }

    /**
     * Internal APIs
     */
    internal val testTaskConfigActions = mutableListOf<Test.(TestTaskContext) -> Unit>()

    /**
     * Private APIs
     */
    private inline fun <reified T: TestSuiteSourceCreationConfig> addSource(action: T.() -> Unit) {
        if (sources.isNotEmpty()) {
            throw RuntimeException(
                "It is not yet possible to register multiple sources for a test suite")
        }
        objects.newInstance(
            T::class.java,
            name
        ).also { newSources ->
            sources.add(newSources)
            action.invoke(newSources)
        }
    }
}
