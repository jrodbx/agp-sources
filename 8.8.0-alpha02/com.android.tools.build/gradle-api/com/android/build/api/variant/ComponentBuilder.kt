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

package com.android.build.api.variant

/**
 * Model for components that only contains configuration-time properties that impacts the build
 * flow.
 *
 * Components can be APKs, AARs, test APKs, unit tests, test fixtures, ...
 *
 * This is the parent interface for all objects that can be manipulated by
 * [AndroidComponentsExtension.beforeVariants]
 *
 * This type is here to mirror the [Component]/[Variant] hierarchy, which also includes
 * [TestComponent] (which is not a [Variant]). In this hierarchy however, there isn't a
 * `TestComponentBuilder` and therefore all objects of this type are also [VariantBuilder]
 *
 * See [VariantBuilder] for more information on accessing instances of this type.
 *
 * The properties exposed by this object have an impact on the build flow. They can add or remove
 * tasks, and change how the tasks consume each other's output. Because of this, they are
 * not using Gradle's [org.gradle.api.provider.Property] so that they can be queried during
 * configuration. Because other applied plugins can change these values in their [beforeVariants]
 * block, it is not safe to read these values in this stage.
 *
 * This object should only be used to set new values. To read the final values of these properties,
 * find the matching property in [Component], and its subtypes
 *
 * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
 * more information
 */
interface ComponentBuilder: ComponentIdentity {

    /**
     * Set to `true` if the variant is active and should be configured, false otherwise.
     *
     * If set to false, the matching [Component] object for this component will not be created.
     */
    var enable: Boolean

    @Deprecated("Will be removed in 9.0")
    var enabled: Boolean
}
