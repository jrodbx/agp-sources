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

package com.android.builder.dexing

import com.android.SdkConstants
import com.android.builder.desugaring.DesugaringClassAnalyzer
import com.android.ide.common.blame.Message
import com.android.tools.r8.origin.ArchiveEntryOrigin
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.origin.PathOrigin
import java.io.File
import java.nio.file.Path
import kotlin.streams.toList

/**
 * Utility to generate the class dependency graph for desugaring.
 *
 * TODO("Remove when D8's new API for desugaring graph computation is used")
 */
object D8DesugarGraphGenerator {

    /**
     * Generates the class dependency graph when desugaring the given class files with the given
     * dexing parameters.
     *
     * The generated graph is returned via the given [D8DesugarGraphConsumer].
     */
    @JvmStatic
    fun generate(
        classFiles: List<ClassFile>,
        dexParams: DexParameters,
        graphConsumer: D8DesugarGraphConsumer
    ) {
        // Special case: When minSdkVersion >= 24, desugaring doesn't require a classpath (see the
        // setup in DexArchiveBuilderTask), so the desugaring graph won't be used.
        if (dexParams.minSdkVersion >= 24) {
            return
        }

        // Map a type to the class file that defines it
        val typeToClassFileMap: MutableMap<String, ClassFile> = mutableMapOf()
        val classpath =
            getClassFiles(dexParams.desugarBootclasspath.paths + dexParams.desugarClasspath.paths)
        for (classFile in classFiles + classpath) {
            // FIXME: This heuristic might have issues with inner classes or single class files
            // containing multiple types. Improve this later.
            val type = classFile.relativePath.replace('\\', '/').substringBefore(".class")
            typeToClassFileMap[type] = classFile
        }

        val visitedSet: MutableSet<ClassFile> = mutableSetOf()
        val toVisitSet: MutableSet<ClassFile> = classFiles.toMutableSet()

        // Compute the dependencies
        while (toVisitSet.isNotEmpty()) {
            val toVisitNextSet = mutableSetOf<ClassFile>()
            for (toVisitNode in toVisitSet) {
                val dependencyTypes =
                    DesugaringClassAnalyzer.computeDependencies(toVisitNode.contents)
                val dependencyClassFiles = dependencyTypes.mapNotNull {
                    if (typeToClassFileMap.containsKey(it)) {
                        typeToClassFileMap[it]
                    } else {
                        // If the type doesn't exist in the input class files and classpath,
                        // normally we would expect it to be an error, but in some cases it could be
                        // caused by an incomplete classpath (typically due to some unusual user
                        // setup, and also because of the heuristic above to map types to class
                        // files), so let's not fail it yet.
                        dexParams.messageReceiver.receiveMessage(
                            Message(
                                kind = Message.Kind.WARNING,
                                text = "D8DesugarGraphGenerator can't resolve type $it"
                            )
                        )
                        null
                    }
                }
                toVisitNextSet.addAll(dependencyClassFiles)

                // Update the desugaring graph
                dependencyClassFiles.forEach { dependency ->
                    check(toVisitNode != dependency)

                    val dependentOrigin = classFileToOrigin(toVisitNode)
                    val dependencyOrigin = classFileToOrigin(dependency)
                    if (dependentOrigin != dependencyOrigin) {
                        // D8 currently emits the edges in reverse, from `dependency` to `dependent`
                        graphConsumer.accept(dependencyOrigin, dependentOrigin)
                    }
                }
            }
            visitedSet.addAll(toVisitSet)
            toVisitSet.clear()
            toVisitSet.addAll(toVisitNextSet - visitedSet)
        }
    }

    /** Returns all the class files on the given classpath. */
    private fun getClassFiles(classpath: List<Path>): List<ClassFile> {
        val classFiles: MutableList<ClassFile> = mutableListOf()
        for (classRoot in classpath) {
            classFiles.addAll(
                ClassFileInputs.fromPath(classRoot).use { classFileInput ->
                    classFileInput.entries { _, _ -> true }.use { classFileStream ->
                        classFileStream.map {
                            ClassFile(it.input.path, it.relativePath, it.readAllBytes())
                        }.toList()
                    }
                }
            )
        }
        return classFiles.toList()
    }

    private fun classFileToOrigin(classFile: ClassFile): Origin {
        return if (classFile.isJarFile) {
            ArchiveEntryOrigin(classFile.relativePath, PathOrigin(classFile.root))
        } else {
            PathOrigin(classFile.root.resolve(classFile.relativePath))
        }
    }

    fun originToFile(origin: Origin): File {
        return when (origin) {
            is PathOrigin -> File(origin.part())
            is ArchiveEntryOrigin -> File(origin.parent().part())
            else -> error("Unexpected type ${origin.javaClass}")
        }
    }
}

/**
 * Simplified version of [ClassFileEntry] which does not require closing resources.
 *
 * TODO("Remove when D8's new API for desugaring graph computation is used")
 */
class ClassFile(val root: Path, val relativePath: String, val contents: ByteArray) {

    val isJarFile: Boolean = root.toString().endsWith(SdkConstants.DOT_JAR, ignoreCase = true)
}

/**
 * Interface of D8's new API for desugaring graph computation.
 *
 * TODO("Remove when D8's new API for desugaring graph computation is used")
 */
interface D8DesugarGraphConsumer {

    /**
     * Callback indicating that code originating from `src` was used to correctly desugar some
     * code originating from `dst`.
     *
     * In other words, `src` is a dependency for the desugaring of `dst`.
     *
     * Note: this callback may be called on multiple threads.
     *
     * Note: this callback places no guarantees on order of calls or on duplicate calls.
     *
     * @param src Origin of some code input that is needed to desugar `dst`.
     * @param dst Origin of some code that was dependent on code in `src`.
     */
    fun accept(src: Origin, dst: Origin)
}