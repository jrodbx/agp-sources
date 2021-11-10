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

package com.android.build.gradle.internal.services

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.api.services.BuildServiceRegistry
import java.util.UUID

/** Registers and configures the build service with the specified type. */
abstract class ServiceRegistrationAction<ServiceT, ParamsT>(
    protected val project: Project,
    private val buildServiceClass: Class<ServiceT>,
    private val maxParalleUsages: Int? = null
) where ServiceT : BuildService<ParamsT>, ParamsT : BuildServiceParameters {
    open fun execute(): Provider<ServiceT> {
        return project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(buildServiceClass),
            buildServiceClass
        ) { buildServiceSpec ->
            buildServiceSpec.parameters?.let { params -> configure(params) }
            maxParalleUsages?.let { buildServiceSpec.maxParallelUsages.set(it) }
        }
    }

    abstract fun configure(parameters: ParamsT)
}

/** Returns the build service with the specified type. Prefer reified [getBuildService] to this method. */
fun <ServiceT : BuildService<out BuildServiceParameters>> getBuildService(
    buildServiceRegistry: BuildServiceRegistry,
    buildServiceClass: Class<ServiceT>
): Provider<ServiceT> {
    @Suppress("UNCHECKED_CAST")
    return (buildServiceRegistry.registrations.getByName(getBuildServiceName(buildServiceClass)) as BuildServiceRegistration<ServiceT, *>).getService()
}

/** Returns the build service of [ServiceT] type. */
inline fun <reified ServiceT : BuildService<out BuildServiceParameters>> getBuildService(buildServiceRegistry: BuildServiceRegistry): Provider<ServiceT> {
    @Suppress("UNCHECKED_CAST")
    return (buildServiceRegistry.registrations.getByName(getBuildServiceName(ServiceT::class.java)) as BuildServiceRegistration<ServiceT, *>).getService()
}

/**
 * Get build service name that works even if build service types come from different class loaders.
 * If the service name is the same, and some type T is defined in two class loaders L1 and L2. E.g.
 * this is true for composite builds and other project setups (see b/154388196).
 *
 * Registration of service may register (T from L1) or (T from L2). This means that querying it with
 * T from other class loader will fail at runtime. This method makes sure both T from L1 and T from
 * L2 will successfully register build services.
 */
fun getBuildServiceName(type: Class<*>): String = type.name + "_" + perClassLoaderConstant

/** Used to get unique build service name. Each class loader will initialize its own version. */
private val perClassLoaderConstant = UUID.randomUUID().toString()
