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

import com.android.build.api.artifact.Artifacts
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

/**
 * Model for components that only contains build-time properties
 *
 * Components can be APKs, AARs, test APKs, unit tests, test fixtures, ...
 *
 * This is the parent interface for all components. From there, there is a first fork, via
 * [Variant] which are production build output (APKs, AARs), and [TestComponent] that are test
 * components. [TestFixtures] is another separate component. See these types for more information.
 *
 * See [Variant] for more information on accessing instances of this type.
 *
 * The properties exposed by this object do not impact the build flow. They are directly set on
 * tasks via [org.gradle.api.provider.Property] linking.
 *
 * For example:
 * ```kotlin
 * tasks.register(...) {
 *   input.set(myComponent.someProperty)
 * * }
 * ```
 *
 * Because links between [org.gradle.api.provider.Property] objects are lazy, they are ordering-safe
 * and any plugins can set new values or link them into their own tasks in any order.
 *
 * However, calling [org.gradle.api.provider.Property.get] during configuration on these properties
 * is not safe as the values could be linked to task output (that requires the task to run).
 *
 * The object also exposes read-only version of the properties exposed by [ComponentBuilder]. These
 * will contain the final values set after all [AndroidComponentsExtension.beforeVariants] callbacks
 * have been executed. It is safe to read these values during configuration time.
 *
 * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
 * more information
 *
 */
interface Component: ComponentIdentity {

    /**
     * Access to the variant's buildable artifacts for build customization.
     */
    val artifacts: Artifacts

    /**
     * Access to variant's source files.
     */
    val sources: Sources

    /**
     * Access to the variant's java compilation options.
     */
    @get:Incubating
    val javaCompilation: JavaCompilation

    @Deprecated("Will be removed in v9.0, use the instrumentation block.")
    fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    )

    @Deprecated("Will be removed in v9.0, use the instrumentation block.")
    fun setAsmFramesComputationMode(mode: FramesComputationMode)

    /**
     * Access to the variant's instrumentation options.
     */
    val instrumentation: Instrumentation

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     */
    val namespace: Provider<String>

    /** Whether the produced artifacts will be debuggable */
    val debuggable: Boolean

    /**
     * Access to the variant's compile classpath.
     *
     * The returned [FileCollection] should not be resolved until execution time.
     */
    @get:Incubating
    val compileClasspath: FileCollection

    /**
     * Access to the variant's compile [Configuration]; for example, the debugCompileClasspath
     * [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    val compileConfiguration: Configuration

    /**
     * Access to the variant's runtime [Configuration]; for example, the debugRuntimeClasspath
     * [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    val runtimeConfiguration: Configuration

    /**
     * Access to the variant's annotation processor [Configuration]; for example, the
     * debugAnnotationProcessor [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    val annotationProcessorConfiguration: Configuration

    /**
     * Provides access to the [LifecycleTasks] created for this component.
     */
    @get:Incubating
    val lifecycleTasks: LifecycleTasks
}
