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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.services.getBuildService
import com.android.builder.errors.IssueReporter
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.util.GradleVersion

object AgpRepositoryChecker {

    private const val CHECK_PERFORMED = "android.agp.repository.check.performed"
    private const val maxProjectsToShow = 3
    private const val JCENTER_URL = "https://jcenter.bintray.com/"

    fun checkRepositories(project: Project) {
        val rootProject = project.rootProject
        if (rootProject.extensions.extraProperties.has(CHECK_PERFORMED)) {
            return
        }
        rootProject.extensions.extraProperties.set(CHECK_PERFORMED, true)

        project.gradle.projectsEvaluated { gradle ->
            val flatDirReposToUsages = mutableMapOf<String, MutableSet<String>>()
            val projectsUsingJCenter = mutableSetOf<String>()

            fun checkSingleRepo(displayName: String, repo: ArtifactRepository) {
                if (repo is FlatDirectoryArtifactRepository) {
                    val projectPaths = flatDirReposToUsages[repo.name] ?: mutableSetOf()
                    projectPaths.add(displayName)
                    flatDirReposToUsages[repo.name] = projectPaths
                }
                if (repo is MavenArtifactRepository && repo.url.toString()==JCENTER_URL) {
                    projectsUsingJCenter.add(displayName)
                }
            }

            gradle.allprojects { p ->
                p.buildscript.repositories.all { checkSingleRepo(p.displayName, it) }
                p.repositories.all { checkSingleRepo(p.displayName, it) }
            }

            try {
                (gradle as GradleInternal).settings.pluginManagement.repositories.all {
                    checkSingleRepo("Gradle Settings", it)
                }
                if (GradleVersion.current() >= GradleVersion.version("6.8")) {
                    // We need to use reflection, as AGP compiles against older Gradle version.
                    val dependencyResolutionManagement =
                            gradle.settings::class.java.getMethod("getDependencyResolutionManagement")
                                    .invoke(gradle.settings)
                    val repositories =
                            dependencyResolutionManagement.javaClass.getMethod("getRepositories")
                                    .invoke(dependencyResolutionManagement) as RepositoryHandler
                    repositories.all {
                        checkSingleRepo("Gradle Settings", it)
                    }
                }
            } catch (ignored: Throwable) {
                // this is using private Gradle APIs, so it may break in the future Gradle versions
            }

            val globalIssues = getBuildService(project.gradle.sharedServices,
                    SyncIssueReporterImpl.GlobalSyncIssueService::class.java).get()

            if (flatDirReposToUsages.isNotEmpty()) {
                val reposWithProjects =
                        flatDirReposToUsages.entries.joinToString(separator = System.lineSeparator()) {
                            "- repository ${it.key} used in: " + it.value.joinToString(
                                    limit = maxProjectsToShow)
                        }
                globalIssues.reportWarning(
                        IssueReporter.Type.GENERIC,
                        """
                            Using flatDirs should be avoided because it doesn't support any meta-data formats.
                            Currently detected usages:
                            $reposWithProjects
                            """.trimIndent(),
                )
            }
            if (projectsUsingJCenter.isNotEmpty()) {
                val listOfProjects =
                        projectsUsingJCenter.joinToString(limit = maxProjectsToShow)
                globalIssues.reportWarning(
                        IssueReporter.Type.JCENTER_IS_DEPRECATED,
                        """
                Please remove usages of `jcenter()` Maven repository from your build scripts and migrate your build to other Maven repositories.
                This repository is deprecated and it will be shut down in the future.
                See http://developer.android.com/r/tools/jcenter-end-of-service for more information.
                Currently detected usages in: $listOfProjects
                """.trimIndent())
            }
        }
    }
}
