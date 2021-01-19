/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.api.transform;


import com.android.annotations.NonNull;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.logging.LoggingManager;
import org.gradle.workers.WorkerExecutor;

/**
 * Context for the transform.
 * <p>
 * This gives access to a limited amount of context when the transform is run.
 */
public interface Context {

    /**
     * Returns the LoggingManager which can be used to control the logging level and standard
     * output/error capture for this task.
     *
     * By default, System.out is redirected to the Gradle logging system at the QUIET log level,
     * and System.err is redirected at the ERROR log level.
     *
     * @return the LoggingManager. Never returns null.
     */
    LoggingManager getLogging();

    /**
     * Returns a directory which this task can use to write temporary files to.
     *
     * Each task instance is provided with a separate temporary directory. There are
     * no guarantees that the contents of this directory will be kept beyond the execution
     * of the task.
     *
     * @return The directory. Never returns null. The directory will already exist.
     */
    File getTemporaryDir();

    /**
     * Returns the path of the task, which is a fully qualified name for the task.
     *
     * The path of a task is the path of its <code>Project</code> plus the name of the task,
     * separated by <code>:</code>.
     *
     * @return the path of the task, which is equal to the path of the project plus the name of the task.
     */
    String getPath();

    /**
     * Returns the project name containing the task.
     *
     * @return the {@link Project#getName()}
     */
    String getProjectName();

    /**
     * Returns the name of the variant.
     *
     * @return the name of the variant.
     */
    @NonNull
    String getVariantName();

    /**
     * Returns the {@link org.gradle.workers.WorkerExecutor} to enlist runnable pieces of work.
     *
     * @return a task level shared instance.
     */
    @NonNull
    WorkerExecutor getWorkerExecutor();
}
