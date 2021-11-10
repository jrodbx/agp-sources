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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to merge all public assets folders into a single one to be fed to robo-electric.
 */
@DisableCachingByDefault
abstract class MergeAssetsForUnitTest: Sync(), VariantAwareTask {

    @get:OutputDirectory
    abstract val outDirectory: DirectoryProperty

    // override to remove the @OutputDirectory annotation
    @Internal
    override fun getDestinationDir(): File {
        return outDirectory.get().asFile
    }

    @get:Internal
    override lateinit var variantName: String

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<MergeAssetsForUnitTest, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("merge", "AssetsForUnitTest")

        override val type: Class<MergeAssetsForUnitTest>
            get() = MergeAssetsForUnitTest::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<MergeAssetsForUnitTest>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeAssetsForUnitTest::outDirectory
            ).withName("out").on(InternalArtifactType.MERGED_ASSETS_FOR_UNIT_TEST)
        }

        override fun configure(
            task: MergeAssetsForUnitTest
        ) {
            super.configure(task)
            task.from(creationConfig.artifacts.getAll(MultipleArtifact.ASSETS))
        }
    }

}
