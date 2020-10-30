/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.services.DslServices;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;

/**
 * Factory to create AndroidSourceSet object using an {@link ObjectFactory} to add the DSL methods.
 */
public class AndroidSourceSetFactory implements NamedDomainObjectFactory<AndroidSourceSet> {

    @NonNull private final Project project;
    private final boolean publishPackage;
    @NonNull private final DslServices dslServices;

    /**
     * Constructor for this AndroidSourceSetFactory.
     *
     * @param project the project for this AndroidSourceSetFactory.
     * @param publishPackage true to set the package name to "publish", false to set it to "apk".
     * @param dslServices dslServices of the project.
     */
    public AndroidSourceSetFactory(
            @NonNull Project project, boolean publishPackage, @NonNull DslServices dslServices) {
        this.publishPackage = publishPackage;
        this.project = project;
        this.dslServices = dslServices;
    }

    @NonNull
    @Override
    public AndroidSourceSet create(@NonNull String name) {
        return dslServices.newInstance(
                DefaultAndroidSourceSet.class, name, project, publishPackage);
    }
}
