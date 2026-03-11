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

package com.android.build.api.component.impl

import android.databinding.tool.DataBindingBuilder
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.gradle.api.AnnotationProcessorOptions
import com.android.build.gradle.internal.dsl.decorator.LockableList
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider

class AnnotationProcessorImpl(
    annotationProcessorOptionsSetInDSL: AnnotationProcessorOptions,
    val dataBindingEnabled: Boolean,
    val internalServices: VariantServices,
): AnnotationProcessor {

    /**
     * These are the public facing [ListProperty] or [MapProperty] for users to dynamically add
     * annotation processors or parameters to annotation processors.
     *
     * These lists will contain all externally added classNames whether it was added from the old
     * variant API using the [com.android.build.gradle.api.BaseVariant.getJavaCompileOptions] or
     * the new Variant API using the [com.android.build.api.variant.Component.javaCompilation]
     * methods.
     *
     * These lists are initialized from the DSL objects (via the merged variant DSL helper classes)
     *
     * Do not use those methods internally to get the final list of annotation processors since we
     * are adding extra annotation processors depending on build features turned on/off, etc..
     * It is therefore ok to use those for mutating the lists but never to get their final values.
     *
     * Once the old variant API is removed, these properties should be disallowed for unsafe read,
     * tracked by b/243199661.
     */
    override val classNames: ListProperty<String> =
        internalServices.listPropertyOf(
            String::class.java,
            annotationProcessorOptionsSetInDSL.classNames,
            false,
        )

    override val arguments: MapProperty<String, String> =
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            annotationProcessorOptionsSetInDSL.arguments,
            false,
        )

    override val argumentProviders: LockableList<CommandLineArgumentProvider> =
        LockableList<CommandLineArgumentProvider>("AnnotationProcessor::argumentProviders")
            .also {
                it.addAll(annotationProcessorOptionsSetInDSL.compilerArgumentProviders)
            }

    /**
     * This is the final list of annotation processor class names as it should be used by
     * compilation related classes.
     * The list contains the provided list from users plus any annotation processor that AGP
     * decided to turn on.
     *
     * This method can only be called during execution time as it resolves Variant APIs [Provider]
     * instances.
     */
    val finalListOfClassNames: Provider<List<String>> =
        classNames.map {
            if (dataBindingEnabled) {
                val updatedListOfClassNames = it.toMutableList()
                // We want to pass data binding processor's class name to the Java compiler.
                // However, if the class names of other annotation processors were not added
                // previously, adding the class name of data binding alone would disable Java
                // compiler's automatic discovery of annotation processors and the other annotation
                // processors would not be invoked.
                // Therefore, we add data binding only if another class name was specified before.

                // so first, we check if is provided from the [arguments]
                val processorsProvidedThroughArguments = arguments.get()["-processor"] != null
                if (arguments.get()["-processor"]?.contains(DataBindingBuilder.PROCESSOR_NAME) == true) {
                    return@map updatedListOfClassNames
                }

                // second, we check if it is provided from the [argumentProviders]
                val argumentProvidersAsString = argumentProviders
                    .filter { provider -> provider !is DataBindingCompilerArguments }
                    .map(CommandLineArgumentProvider::asArguments)
                    .flatten()
                    .joinToString()
                val processorsProvidedThroughArgumentProviders = argumentProvidersAsString
                    .contains("-processor")
                if (processorsProvidedThroughArgumentProviders &&
                    argumentProvidersAsString.contains(DataBindingBuilder.PROCESSOR_NAME)) {
                        return@map updatedListOfClassNames
                }

                // finally, if other processors were specified through arguments or
                // argumentProviders, we must add the databinding one.
                // otherwise, check [classNames] and add it if it is not empty and not present.
                if (processorsProvidedThroughArguments ||
                    processorsProvidedThroughArgumentProviders ||
                    (it.isNotEmpty() && !it.contains(DataBindingBuilder.PROCESSOR_NAME))) {
                    updatedListOfClassNames.add(DataBindingBuilder.PROCESSOR_NAME)
                }
                updatedListOfClassNames
            } else {
                // if databinding is not enabled, just return the collection unmodified.
                it
            }
        }
}
