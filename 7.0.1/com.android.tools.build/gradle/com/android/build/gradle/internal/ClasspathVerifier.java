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

package com.android.build.gradle.internal;

import com.google.common.util.concurrent.MoreExecutors;

public class ClasspathVerifier {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void checkClasspathSanity() {
        try {
            MoreExecutors.directExecutor();
        } catch (NoSuchMethodError e) {
            throw new RuntimeException(
                    "You appear to have guava-jdk5 on your project buildScript or buildSrc classpath.\n"
                            + "This is likely a transitive dependency of another gradle plugin."
                            + "Run the buildEnvironment task to find out more.\n"
                            + "See https://issuetracker.google.com/38419426#comment8 for a workaround.");
        }
    }
}
