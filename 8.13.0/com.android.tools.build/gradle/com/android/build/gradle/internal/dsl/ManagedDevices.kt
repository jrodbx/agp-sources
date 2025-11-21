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
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer
import javax.inject.Inject

open class ManagedDevices @Inject constructor(dslServices: DslServices) :
    com.android.build.api.dsl.ManagedDevices {

    override val allDevices: ExtensiblePolymorphicDomainObjectContainer<Device> =
        dslServices.polymorphicDomainObjectContainer(Device::class.java).apply {
            registerBinding(
                com.android.build.api.dsl.ManagedVirtualDevice::class.java,
                ManagedVirtualDevice::class.java
            )
            whenObjectAdded { device: Device ->
                if (device is com.android.build.api.dsl.ManagedVirtualDevice) {
                    localDevices.add(device)
                }
            }
            whenObjectRemoved { device: Device ->
                if (device is com.android.build.api.dsl.ManagedVirtualDevice) {
                    localDevices.remove(device)
                }
            }
        }

    override val localDevices:
            NamedDomainObjectContainer<com.android.build.api.dsl.ManagedVirtualDevice> =
        dslServices.domainObjectContainer(
            com.android.build.api.dsl.ManagedVirtualDevice::class.java,
            ManagedVirtualDeviceFactory(dslServices)
        ).apply {
            whenObjectAdded { device: com.android.build.api.dsl.ManagedVirtualDevice ->
                allDevices.add(device)
            }
            whenObjectRemoved { device: com.android.build.api.dsl.ManagedVirtualDevice ->
                allDevices.remove(device)
            }
        }

    override val devices: ExtensiblePolymorphicDomainObjectContainer<Device> = allDevices

    override val groups: NamedDomainObjectContainer<DeviceGroup> =
        dslServices.domainObjectContainer(DeviceGroup::class.java, DeviceGroupFactory(dslServices))
}
