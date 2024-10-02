/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.testing

import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.manageddevice.DeviceDslRegistration
import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.android.build.gradle.internal.core.dsl.features.AndroidTestOptionsDslInfo

/**
 * Implementation class of the Managed Device Registry.
 */
class ManagedDeviceRegistry(
    private val testOptions: AndroidTestOptionsDslInfo
) : com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry {

    /**
     * List of all valid registrations for the given registry.
     */
    private val registrationList: MutableList<Registration<*>> = mutableListOf()

    /**
     * Cached map of the Gradle Decorated Implementation cache to the appropriate Registration.
     *
     * Since the actual classes in the [Managed Device Block][ManagedDevices.allDevices] are
     * decorated Gradle classes, we have to check for the registration via ```instanceof``` the
     * implementation. This is not ideal, so we can at least cache the registrations based on the
     * decorated classes as the key for future calls to [get]
     */
    private val decoratedDeviceCache: MutableMap<Class<out Device>, Registration<*>> =
        mutableMapOf()

    /**
     * Tracks the api and implementation classes that are being used, to ensure the same class
     * is not used twice.
     */
    private val registeredDSLClasses: MutableSet<Class<out Device>> = mutableSetOf()

    /**
     * Gets the Registration associated with the given Decorated Device class. If no such
     * class is registered, then null is returned.
     *
     * @param DecoratedDeviceT the class of given Decorated Device
     * @param clazz the Class object for DecoratedDeviceT
     * @return the registration associated with the decorated class, or null if no
     * such registration exists.
     */
    @Suppress("UNCHECKED_CAST")
    fun <DecoratedDeviceT: Device> get(
        clazz: Class<DecoratedDeviceT>
    ): Registration<in DecoratedDeviceT>? {
        if (decoratedDeviceCache.containsKey(clazz)) {
            return decoratedDeviceCache[clazz] as Registration<in DecoratedDeviceT>
        }
        return registrationList.firstOrNull {
            it.deviceImpl.isAssignableFrom(clazz)
        }?.apply {
            decoratedDeviceCache[clazz] = this
        } as Registration<in DecoratedDeviceT>?
    }

    override fun <DeviceT : Device> registerDeviceType(
        dslInterface: Class<DeviceT>,
        setupBlock: DeviceDslRegistration<DeviceT>.() -> Unit
    ) {
        val builder = DeviceDslRegistrationBuilder(dslInterface)
        setupBlock(builder)
        val registration: Registration<DeviceT> = builder.toRegistration()

        // Ensure that the DSL hasn't already be registered as API or Implementation.
        val apiClass = registration.deviceApi
        val implementationClass = registration.deviceImpl

        if (registeredDSLClasses.contains(implementationClass)) {
            error("Custom Device Implementation Class: $implementationClass " +
                          "is already registered with the Managed Device Registry.")
        }
        if (registeredDSLClasses.contains(apiClass)) {
            error("Custom Device Api Class: $apiClass " +
                          "is already registered with the Managed Device Registry.")
        }
        // Ensure that an implementation is not a subclass of another implementation or vice versa.
        registrationList.firstOrNull {
            it.deviceImpl.isAssignableFrom(implementationClass)
        }?.apply {
            error ("Cannot register Implementation Class: $implementationClass " +
                           "as it is a subclass of an already registered implementation: $this")
        }
        registrationList.firstOrNull {
            implementationClass.isAssignableFrom(it.deviceImpl)
        }?.apply {
            error("Cannot register Implementation Class: $implementationClass " +
                          "as it is a superclass of an already registered implementation: $this")
        }
        // Now the registration is considered valid, update the binding.
        testOptions.managedDevices.allDevices.registerBinding(
            apiClass,
            implementationClass
        )
        // update the registration and tracked classes
        registrationList.add(registration)
        registeredDSLClasses.add(apiClass)
        registeredDSLClasses.add(implementationClass)
    }

    class DeviceDslRegistrationBuilder<DeviceT: Device>(val dslInterface : Class<DeviceT>)
        : DeviceDslRegistration<DeviceT> {

        override lateinit var dslImplementationClass: Class<out DeviceT>
        var setupConfigAction : Class<out DeviceSetupConfigureAction<DeviceT, *>>? = null
        var setupTaskAction: Class<out DeviceSetupTaskAction<*>>? = null
        lateinit var testRunConfigAction : Class<out DeviceTestRunConfigureAction<DeviceT, *>>
        lateinit var testRunTaskAction: Class<out DeviceTestRunTaskAction<*>>

        override fun <SetupInputT : DeviceSetupInput> setSetupActions(
            configureAction: Class<out DeviceSetupConfigureAction<DeviceT, SetupInputT>>,
            taskAction: Class<out DeviceSetupTaskAction<SetupInputT>>
        ) {
            setupConfigAction = configureAction
            setupTaskAction = taskAction
        }

        override fun <TestRunInputT : DeviceTestRunInput> setTestRunActions(
            configureAction: Class<out DeviceTestRunConfigureAction<DeviceT, TestRunInputT>>,
            taskAction: Class<out DeviceTestRunTaskAction<TestRunInputT>>
        ) {
            testRunConfigAction = configureAction
            testRunTaskAction = taskAction
        }

        fun toRegistration(): Registration<DeviceT> {
            return Registration(
                dslInterface,
                dslImplementationClass,
                setupConfigAction,
                setupTaskAction,
                testRunConfigAction,
                testRunTaskAction,
            )
        }
    }

    data class Registration<DeviceT: Device> (
        val deviceApi : Class<DeviceT>,
        val deviceImpl : Class<out DeviceT>,
        val setupConfigAction : Class<out DeviceSetupConfigureAction<DeviceT, *>>?,
        val setupTaskAction: Class<out DeviceSetupTaskAction<*>>?,
        val testRunConfigAction : Class<out DeviceTestRunConfigureAction<DeviceT, *>>,
        val testRunTaskAction: Class<out DeviceTestRunTaskAction<*>>,
    ) {
        val hasSetupActions: Boolean
            get() = setupConfigAction != null && setupTaskAction != null
    }
}
