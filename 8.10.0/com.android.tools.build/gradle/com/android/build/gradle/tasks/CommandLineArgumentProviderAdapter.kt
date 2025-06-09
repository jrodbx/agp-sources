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

package com.android.build.gradle.tasks

import android.databinding.tool.DataBindingBuilder
import com.google.common.base.Joiner
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

class CommandLineArgumentProviderAdapter(
    @get:Input
    val classNames: Provider<List<String>>,

    @get:Input
    val arguments: Provider<Map<String, String>>
): CommandLineArgumentProvider {

    override fun asArguments(): MutableIterable<String> {
        return mutableListOf<String>().also {
            if (classNames.get().isNotEmpty()) {
                it.add("-processor")
                it.add(Joiner.on(',').join(classNames.get()))
            }

            for ((key, value) in arguments.get()) {
                it.add("-A$key=$value")
            }
        }
    }
}
