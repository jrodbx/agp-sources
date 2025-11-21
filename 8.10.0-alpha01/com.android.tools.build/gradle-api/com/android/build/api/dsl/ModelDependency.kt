/*
 * Copyright (C) 2024 The Android Open Source Project
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
import org.gradle.api.provider.Property

/** DSL object to configure a model dependency for an AI pack. */
@Incubating
interface ModelDependency {
    /**
     * The package name of the app that contains this model dependency.
     */
    @get:Incubating
    val aiModelPackageName: Property<String>
    /**
     * The name of the AI model dependency.
     */
    @get:Incubating
    val aiModelName: Property<String>
}
