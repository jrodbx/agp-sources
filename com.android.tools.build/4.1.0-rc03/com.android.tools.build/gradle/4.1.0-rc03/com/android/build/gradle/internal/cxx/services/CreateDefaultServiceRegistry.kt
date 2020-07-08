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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.scope.GlobalScope


/**
 * Create the default service registry for build model.
 */
fun createBuildModelServiceRegistry() : CxxServiceRegistry {
    val registry = CxxServiceRegistryBuilder()
    createFinishListenerService(registry)
    createBuildModelListenerService(registry)
    return registry.build()
}

/**
 * Create the default module-level service registry.
 */
fun createDefaultServiceRegistry(global : GlobalScope) : CxxServiceRegistry {
    val registry = CxxServiceRegistryBuilder()
    createProcessJunctionService(registry)
    createIssueReporterService(global, registry)
    createModelDependencyService(global, registry)
    return registry.build()
}

/**
 * Create the default module-level service registry.
 */
fun createDefaultAbiServiceRegistry() : CxxServiceRegistry {
    val registry = CxxServiceRegistryBuilder()
    createSyncListenerService(registry)
    return registry.build()
}