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

package com.android.build.gradle.internal.services

import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

class TaskCreationServicesImpl(projectServices: ProjectServices) :
    BaseServicesImpl(projectServices), TaskCreationServices {

    override fun file(file: Any): File = projectServices.fileResolver(file)

    override fun fileCollection(): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection()

    override fun fileCollection(vararg files: Any): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection().from(*files)
}