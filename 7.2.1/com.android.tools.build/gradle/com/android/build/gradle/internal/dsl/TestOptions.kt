/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.build.api.dsl.Device
import com.android.build.api.dsl.DeviceGroup
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.TestOptions.Execution
import com.android.utils.HelpfulEnumConverter
import com.google.common.base.Preconditions
import com.google.common.base.Verify
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.util.ConfigureUtil
import javax.inject.Inject

open class TestOptions @Inject constructor(dslServices: DslServices) :
    com.android.build.api.dsl.TestOptions {
    private val executionConverter = HelpfulEnumConverter<Execution>(Execution::class.java)

    private var _execution = Execution.HOST

    override val unitTests: UnitTestOptions =
        dslServices.newInstance(UnitTestOptions::class.java, dslServices)

    override var resultsDir: String? = null
    override var reportDir: String? = null
    override var animationsDisabled: Boolean = false

    override val devices: ExtensiblePolymorphicDomainObjectContainer<Device>
        get() = managedDevices.devices

    override val deviceGroups: NamedDomainObjectContainer<DeviceGroup>
        get() = managedDevices.groups

    override val managedDevices: ManagedDevices =
        dslServices.newInstance(ManagedDevices::class.java, dslServices)

    // (Implementing interface for kotlin)
    override fun managedDevices(action: com.android.build.api.dsl.ManagedDevices.() -> Unit) {
        action.invoke(managedDevices)
    }

    // Runtime only for groovy decorator to generate the closure based block
    fun managedDevices(action: Action<com.android.build.api.dsl.ManagedDevices>) {
        action.execute(managedDevices)
    }

    override var execution: String
        get() = Verify.verifyNotNull(
            executionConverter.reverse().convert(_execution),
            "No string representation for enum."
        )
        set(value) {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            _execution = Preconditions.checkNotNull(
                executionConverter.convert(value),
                "The value of `execution` cannot be null."
            )!!
        }

    override val emulatorSnapshots: com.android.build.api.dsl.EmulatorSnapshots =
        dslServices.newInstance(EmulatorSnapshots::class.java, dslServices)

    @Deprecated("Renamed to emulatorSnapshots", replaceWith = ReplaceWith("emulatorSnapshots"))
    override val failureRetention: com.android.build.api.dsl.FailureRetention
        get() = emulatorSnapshots as com.android.build.api.dsl.FailureRetention

    override fun unitTests(action: com.android.build.api.dsl.UnitTestOptions.() -> Unit) {
        action.invoke(unitTests)
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    fun unitTests(closure: Closure<*>) {
        ConfigureUtil.configure(closure, unitTests)
    }

    fun unitTests(action: Action<UnitTestOptions>) {
        action.execute(unitTests)
    }

    fun getExecutionEnum(): Execution = _execution

    open class UnitTestOptions @Inject constructor(dslServices: DslServices) :
        com.android.build.api.dsl.UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        private val testTasks = dslServices.domainObjectSet(Test::class.java)

        override var isReturnDefaultValues: Boolean = false
        override var isIncludeAndroidResources: Boolean = false

        fun all(configClosure: Closure<Test>) {
            testTasks.all {
                ConfigureUtil.configure(configClosure, it)
            }
        }

        override fun all(configAction: (Test) -> Unit) {
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

    // (Implementing interface for kotlin)
    override fun emulatorSnapshots(action: com.android.build.api.dsl.EmulatorSnapshots.() -> Unit) {
        action.invoke(emulatorSnapshots)
    }

    // Runtime only for groovy decorator to generate the closure based block
    fun emulatorSnapshots(action: Action<com.android.build.api.dsl.EmulatorSnapshots>) {
        action.execute(emulatorSnapshots)
    }

    @Deprecated("Renamed to emulatorSnapshots", replaceWith = ReplaceWith("emulatorSnapshots"))
    override fun failureRetention(action: com.android.build.api.dsl.FailureRetention.() -> Unit) {
        action.invoke(failureRetention)
    }

    // Runtime only for groovy decorator to generate the closure based block
    @Deprecated("Renamed to emulatorSnapshots", replaceWith = ReplaceWith("emulatorSnapshots"))
    fun failureRetention(action: Action<com.android.build.api.dsl.FailureRetention>) {
        action.execute(failureRetention)
    }
}
