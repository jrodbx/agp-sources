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

interface Component: ComponentIdentity {

    /**
     * Access to the variant's buildable artifacts for build customization.
     */
    val artifacts: Artifacts

    /**
     * Access to variant's source files.
     */
    @get:Incubating
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
    @get:Incubating
    val compileConfiguration: Configuration

    /**
     * Access to the variant's runtime [Configuration]; for example, the debugRuntimeClasspath
     * [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    @get:Incubating
    val runtimeConfiguration: Configuration

    /**
     * Access to the variant's annotation processor [Configuration]; for example, the
     * debugAnnotationProcessor [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    @get:Incubating
    val annotationProcessorConfiguration: Configuration
}
