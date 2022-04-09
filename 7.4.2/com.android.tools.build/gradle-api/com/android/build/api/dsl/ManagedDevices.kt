/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer

/** Options for Managed Devices */
@Incubating
interface ManagedDevices {

    /**
     * List of test devices for this project for use with the Unified Test Platform
     *
     * These APIs are experimental and may change without notice.
     */
    val devices: ExtensiblePolymorphicDomainObjectContainer<Device>

    /**
     * List of DeviceGroups to create tasks for.
     *
     * These APIs are experimental and may change without notice.
     */
    val groups: NamedDomainObjectContainer<DeviceGroup>
}
