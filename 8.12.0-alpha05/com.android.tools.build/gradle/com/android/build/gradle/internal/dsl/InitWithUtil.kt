/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.dsl.HasInitWith
import org.gradle.api.plugins.ExtensionAware

/**
 * Given an extensionAware object, go through all extensions and call initWith if it is present.
 *
 * See BuildTypeTest.initWith
 */
fun initExtensions(from: ExtensionAware, to: ExtensionAware) {
    for (schema in to.extensions.extensionsSchema) {
        if (HasInitWith::class.java.isAssignableFrom(schema.publicType.concreteClass)) {
            val toExtension = to.extensions.getByName(schema.name)
            @Suppress("UNCHECKED_CAST")
            toExtension as HasInitWith<Any>
            from.extensions.findByName(schema.name)?.let { fromExtension ->
                toExtension.initWith(fromExtension)
            }
        }
    }
}
