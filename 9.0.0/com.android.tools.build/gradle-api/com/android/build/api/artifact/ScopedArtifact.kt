/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.Directory

/**
 * List of [ScopedArtifacts.Scope] artifacts.
 */
sealed class ScopedArtifact: Artifact.Single<Directory>(DIRECTORY, Category.INTERMEDIATES) {

    /**
     * .class files, result of sources compilation and/or external dependencies depending on the
     * scope; includes users' transformation, but does not include Jacoco instrumentation
     *
     * content of [POST_COMPILATION_CLASSES] plus anything added to [CLASSES] after
     * compilation.
     *
     * You should use this if you just need to consume all the classes that will be packaged in
     * the project.
     *
     * If you need to generate bytecodes post compilation and also need to have access to the
     * compiled classes, you cannot directly consume [CLASSES] and append to it as it would create
     * a circular dependency.
     *
     * Instead, consume [POST_COMPILATION_CLASSES] and use [CLASSES] to append your bytecodes to.
     *
     * ```kotlin
     * extension.onVariants(extension.selector().withBuildType("debug")) {  variant ->
     *     val generatorTask = project.tasks.register(
     *          "postCompilation${variant.name.capitalizeFirstChar()}Task",
     *          AddPostCompilationCodeGeneratorTask::class.java) { task ->
     *              // configure your task
     *          }
     *
     *     // consume the result of compilation
     *     variant.artifacts.forScope(Scope.PROJECT)
     *          .use(generatorTask)
     *          .toGet(
     *              ScopedArtifact.POST_COMPILATION_CLASSES,
     *              AddPostCompilationCodeGeneratorTask::jars,
     *              AddPostCompilationCodeGeneratorTask::dirs
     *     )
     *
     *     // and produce the new bytecodes.
     *     variant.artifacts.forScope(Scope.PROJECT)
     *         .use(generatorTask)
     *         .toAppend(ScopedArtifact.CLASSES, AddPostCompilationCodeGeneratorTask::outputDir)
     * }
     * ```
     */
    object CLASSES: ScopedArtifact(), Appendable, Transformable, Replaceable

    /**
     * .class files, result of sources compilation and/or external dependencies depending on the
     * scope
     *
     * Do not use this unless you are producing bytecodes post compilation, see [CLASSES].
     * This artifact is only providing a readonly view of the compiled classes, to add or replace
     * classes, use [CLASSES]
     *
     * To add new bytecode or transform existing bytecode, use [CLASSES].
     * Bytecode added to [CLASSES], or transformed [CLASSES] will not impact the content of
     * [POST_COMPILATION_CLASSES]
     *
     * It is not possible to register such bytecode generator through the DSL, one must use this
     * Variant API.
     *
     * The classes registered through this API will be visible to the
     * [com.android.build.api.variant.Instrumentation] pipeline.
     */
    @Incubating
    object POST_COMPILATION_CLASSES: ScopedArtifact()

    object JAVA_RES: ScopedArtifact(), Appendable, Transformable, Replaceable
}
