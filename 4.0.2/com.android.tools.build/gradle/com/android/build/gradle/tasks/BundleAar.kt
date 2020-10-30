/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.util.Locale

/** Custom Zip task to allow archive name to be set lazily. */
abstract class BundleAar : Zip(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localAarDeps: ConfigurableFileCollection

    @get:Input
    lateinit var projectPath: String
        private set

    @get:Input
    val hasLocalAarDeps: Boolean
        get() {
            val hasLocalAarDependencies = localAarDeps.files.isNotEmpty()
            if (hasLocalAarDependencies) {
                throw RuntimeException(
                    "Direct local .aar file dependencies are not supported when building an AAR. " +
                            "The resulting AAR would be broken because the classes and Android " +
                            "resources from any local .aar file dependencies would not be " +
                            "packaged in the resulting AAR. Previous versions of the Android " +
                            "Gradle Plugin produce broken AARs in this case too (despite not " +
                            "throwing this error). The following direct local .aar file " +
                            "dependencies of the $projectPath project caused this error: " +
                            localAarDeps.files.joinToString { it.absolutePath }
                )
            }
            return hasLocalAarDependencies
        }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<BundleAar>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("bundle", "Aar")
        override val type: Class<BundleAar>
            get() = BundleAar::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleAar>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.bundleLibraryTask = taskProvider
            variantScope.artifacts.producesFile(
                InternalArtifactType.AAR,
                taskProvider,
                BundleAar::getArchiveFile
            )
        }

        override fun configure(task: BundleAar) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            val buildFeatures = variantScope.globalScope.buildFeatures

            // Sanity check, there should never be duplicates.
            task.duplicatesStrategy = DuplicatesStrategy.FAIL
            // Make the AAR reproducible. Note that we package several zips inside the AAR, so all of
            // those need to be reproducible too before we can switch this on.
            // https://issuetracker.google.com/67597902
            task.isReproducibleFileOrder = true
            task.isPreserveFileTimestamps = false

            task.description = ("Assembles a bundle containing the library in "
                    + variantScope.variantDslInfo.componentIdentity.name
                    + ".")

            task.archiveFileName.set(variantScope.variantData.publicVariantPropertiesApi
                .outputs.getMainSplit().apkData.outputFileName)
            task.destinationDirectory.set(File(variantScope.aarLocation.absolutePath))
            task.archiveExtension.set(BuilderConstants.EXT_LIB_ARCHIVE)

            if (buildFeatures.aidl) {
                task.from(
                    variantScope.artifacts.getFinalProduct(
                        InternalArtifactType.AIDL_PARCELABLE
                    ),
                    prependToCopyPath(SdkConstants.FD_AIDL)
                )
            }

            task.from(artifacts.getFinalProduct(
                InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE))

            if (buildFeatures.dataBinding) {
                task.from(
                    variantScope.globalScope.project.provider {
                        variantScope.artifacts.getFinalProduct(
                            InternalArtifactType.DATA_BINDING_ARTIFACT) },
                    prependToCopyPath(DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR)
                )
                task.from(
                    variantScope.artifacts.getFinalProduct(
                        InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                    prependToCopyPath(
                        DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR
                    )
                )
            }

            if (!variantScope.globalScope.extension.aaptOptions.namespaced) {
                // TODO: this should be unconditional b/69358522
                task.from(
                    artifacts.getFinalProduct(
                        InternalArtifactType.COMPILE_SYMBOL_LIST))
                task.from(
                    artifacts.getFinalProduct(InternalArtifactType.PACKAGED_RES),
                    prependToCopyPath(SdkConstants.FD_RES)
                )
                // In non-namespaced projects bundle the library manifest straight to the AAR.
                task.from(artifacts.getFinalProduct(InternalArtifactType.LIBRARY_MANIFEST))
            } else {
                // In namespaced projects the bundled manifest needs to have stripped resource
                // references for backwards compatibility.
                task.from(artifacts.getFinalProduct(
                    InternalArtifactType.NON_NAMESPACED_LIBRARY_MANIFEST))
            }

            if (buildFeatures.renderScript) {
                task.from(
                    artifacts.getFinalProduct(InternalArtifactType.RENDERSCRIPT_HEADERS),
                    prependToCopyPath(SdkConstants.FD_RENDERSCRIPT)
                )
            }

            task.from(artifacts.getFinalProduct(InternalArtifactType.PUBLIC_RES))
            if (artifacts.hasFinalProduct(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)) {
                task.from(artifacts.getFinalProduct(
                    InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR))
            }
            task.from(artifacts.getFinalProductAsFileCollection(InternalArtifactType.RES_STATIC_LIBRARY))
            task.from(
                artifacts.getFinalProduct(LIBRARY_AND_LOCAL_JARS_JNI),
                prependToCopyPath(SdkConstants.FD_JNI)
            )
            task.from(variantScope.globalScope.artifacts
                .getFinalProduct(InternalArtifactType.LINT_PUBLISH_JAR))
            task.from(artifacts.getFinalProduct(InternalArtifactType.ANNOTATIONS_ZIP))
            task.from(artifacts.getFinalProduct(InternalArtifactType.AAR_MAIN_JAR))
            task.from(
                artifacts.getFinalProduct(InternalArtifactType.AAR_LIBS_DIRECTORY),
                prependToCopyPath(SdkConstants.LIBS_FOLDER)
            )
            task.from(
                variantScope.artifacts
                    .getFinalProduct(InternalArtifactType.LIBRARY_ASSETS),
                prependToCopyPath(SdkConstants.FD_ASSETS))
            task.localAarDeps.from(
                variantScope.getLocalFileDependencies {
                    it.name.toLowerCase(Locale.US).endsWith(SdkConstants.DOT_AAR)
                }
            )
            task.projectPath = variantScope.globalScope.project.path
        }

        private fun prependToCopyPath(pathSegment: String) = Action { copySpec: CopySpec ->
            copySpec.eachFile { fileCopyDetails: FileCopyDetails ->
                fileCopyDetails.relativePath =
                        fileCopyDetails.relativePath.prepend(pathSegment)
            }
        }
    }
}
