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
package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantServices

/**
 * Data about a test component in a normal plugin
 *
 *
 * For the test plugin, ApplicationVariantData is used.
 */
class TestVariantData(
    componentIdentity: ComponentIdentity,
    artifacts: ArtifactsImpl,
    services: VariantServices,
    taskContainer: MutableTaskContainer
) : ApkVariantData(
    componentIdentity,
    artifacts,
    services,
    taskContainer
)
