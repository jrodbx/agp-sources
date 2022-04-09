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
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.dsl.TestOptions
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceDslRegistration
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceSetupFactory
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceTestRunFactory
import org.gradle.api.provider.Provider

/**
 * Implementation class of the Custom Managed Device Registry
 */
class CustomManagedDeviceRegistry(
    val testOptions: Provider<TestOptions>
) : com.android.build.api.instrumentation.manageddevice.CustomManagedDeviceRegistry {

    /**
     * List of all valid registrations for the given registry.
     */
    private val registrationList: MutableList<Registration<*, *, *>> = mutableListOf()

    /**
     * Cached map of the Gradle Decorated Implementation cache to the appropriate Registration.
     *
     * Since the actual classes in the [Managed Device Block][ManagedDevices.devices] are decorated
     * Gradle classes, we have to check for the registration via ```instanceof``` the
     * implementation. This is not ideal, so we can at least cache the registrations based on the
     * decorated classes as the key for future calls to [get]
     */
    private val decoratedDeviceCache: MutableMap<Class<out Device>, Registration<*, *, *>> =
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
    ): Registration<in DecoratedDeviceT, *, *>? {
        if (decoratedDeviceCache.containsKey(clazz)) {
            return decoratedDeviceCache[clazz] as Registration<in DecoratedDeviceT, *, *>
        }
        return registrationList.firstOrNull {
            it.dsl.deviceImpl.isAssignableFrom(clazz)
        }?.apply {
            decoratedDeviceCache[clazz] = this
        } as Registration<in DecoratedDeviceT, *, *>?
    }

    override fun <
            DeviceT: Device,
            TestRunInputT: DeviceTestRunInput
    > registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    ) = registerInternal(
            Registration<DeviceT, Nothing, TestRunInputT>(
                dsl,
                null,
                testRunFactory
            )
        )

    override fun <
            DeviceT: Device,
            SetupInputT: DeviceSetupInput,
            TestRunInputT: DeviceTestRunInput
    > registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        setupFactory: ManagedDeviceSetupFactory<DeviceT, SetupInputT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    ) = registerInternal(
            Registration(
                dsl,
                setupFactory,
                testRunFactory
            )
        )

    private fun <DeviceT: Device> registerInternal(
        registration: Registration<DeviceT, *, *>
    ) {
        // Ensure that the DSL hasn't already be registered as API or Implementation.
        val apiClass = registration.dsl.deviceApi
        val implementationClass = registration.dsl.deviceImpl

        if (registeredDSLClasses.contains(implementationClass)) {
            error("""
                Custom Device Implementation Class: $implementationClass
                is already registered with the Custom Managed Device Registry.
            """.trimIndent())
        }
        if (registeredDSLClasses.contains(apiClass)) {
            error("""
                Custom Device Api Class: $apiClass
                is already registered with the Custom Managed Device Registry.
            """.trimIndent())
        }
        // Ensure that an implementation is not a subclass of another implementation or vice versa.
        registrationList.firstOrNull {
            it.dsl.deviceImpl.isAssignableFrom(implementationClass)
        }?.apply {
            error ("""
                Cannot register Implementation Class: $implementationClass
                as it is a subclass of an already registered implementation: $this
            """.trimIndent())
        }
        registrationList.firstOrNull {
            implementationClass.isAssignableFrom(it.dsl.deviceImpl)
        }?.apply {
            error("""
                Cannot register Implementation Class: $implementationClass
                as it is a superclass of an already registered implementation: $this
            """.trimIndent())
        }
        // Now the registration is considered valid, update the binding.
        testOptions.get().managedDevices.devices.registerBinding(
            apiClass,
            implementationClass
        )
        // update the registration and tracked classes
        registrationList.add(registration)
        registeredDSLClasses.add(apiClass)
        registeredDSLClasses.add(implementationClass)
    }

    data class Registration<
            DeviceT: Device,
            SetupInputT: DeviceSetupInput,
            TestRunInputT: DeviceTestRunInput
    > (
        val dsl: ManagedDeviceDslRegistration<DeviceT>,
        val setupFactory: ManagedDeviceSetupFactory<DeviceT, SetupInputT>?,
        val testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    )
}
