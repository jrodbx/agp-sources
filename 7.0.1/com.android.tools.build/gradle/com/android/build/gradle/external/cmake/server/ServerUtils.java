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

package com.android.build.gradle.external.cmake.server;

import com.android.annotations.NonNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Utility class for Cmake server. */
public class ServerUtils {
    /**
     * Validates the given hello result object.
     *
     * @param helloResult - given hello result received from Cmake server
     * @return true if the given HelloResult object is valid
     */
    public static boolean isHelloResultValid(HelloResult helloResult) {
        return (helloResult != null
                && helloResult.type != null
                && helloResult.supportedProtocolVersions != null
                && helloResult.type.equals("hello")
                && helloResult.supportedProtocolVersions.length >= 1);
    }

    /**
     * Validates the given handshake result object.
     *
     * @param handshakeResult - given handshake result received from Cmake server
     * @return true if the given HandshakeResult object is valid
     */
    public static boolean isHandshakeResultValid(@NonNull HandshakeResult handshakeResult) {
        return (handshakeResult.cookie != null
                && handshakeResult.inReplyTo != null
                && handshakeResult.type != null);
    }

    /**
     * Validates the given response to configure command from Cmake server
     *
     * @return true if the given ConfigureResult is valid
     */
    public static boolean isConfigureResultValid(ConfigureResult configureResult) {
        return (configureResult != null
                && configureResult.type != null
                && configureResult.inReplyTo != null
                && configureResult.type.equals("reply")
                && configureResult.inReplyTo.equals("configure"));
    }

    /**
     * Validates if the result of compute from Cmake server is valid
     *
     * @return true if the given ComputeResult is valid
     */
    public static boolean isComputedResultValid(@NonNull ComputeResult computeResult) {
        return (computeResult.inReplyTo != null
                && computeResult.type != null
                && computeResult.inReplyTo.equals("compute")
                && computeResult.type.equals("reply"));
    }

    /**
     * Validates if the response to code model from Cmake server is valid
     *
     * @return true if the given CodeModel has all the fields that are required by gradle to work as
     *     expected.
     */
    public static boolean isCodeModelValid(@NonNull CodeModel codeModel) {
        return (codeModel.type != null
                && codeModel.inReplyTo != null
                && codeModel.inReplyTo.equals("codemodel")
                && codeModel.type.equals("reply")
                && codeModel.configurations != null
                && isCodeModelConfigurationsValid(codeModel.configurations));
    }

    /**
     * Validates if the response to cmake inputs from Cmake server is valid
     *
     * @return true if the given cmake Inputs has all the fields that are required by gradle to work
     *     as expected.
     */
    public static boolean isCmakeInputsResultValid(@NonNull CmakeInputsResult cmakeInputsResult) {
        return (cmakeInputsResult.type != null
                && cmakeInputsResult.inReplyTo != null
                && cmakeInputsResult.inReplyTo.equals("cmakeInputs"));
    }

    /**
     * Validates if the configuration in the code model from Cmake server is valid
     *
     * @return true if the given Configuration has all the fields that are required by gradle to
     *     work as expected.
     */
    private static boolean isCodeModelConfigurationsValid(@NonNull Configuration configurations[]) {
        if (configurations.length <= 0) {
            return false;
        }

        for (Configuration configuration : configurations) {
            if (configuration.projects == null || configuration.projects.length <= 0) {
                return false;
            }

            for (Project project : configuration.projects) {
                if (!isCodeModelProjectValid(project)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Validates if the project in the code model from Cmake server is valid
     *
     * @return true if the given Project has all the fields that are required by gradle to work as
     *     expected.
     */
    private static boolean isCodeModelProjectValid(Project project) {
        if (project == null || project.buildDirectory == null || project.sourceDirectory == null) {
            return false;
        }

        // A project with empty targets is considered to be valid.
        if (project.targets == null) {
            return true;
        }

        for (Target target : project.targets) {
            if (!isCodeModelTargetValid(target)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates if the target in the code model from Cmake server is valid
     *
     * @return true if the given Target has all the fields that are required by gradle to work as
     *     expected.
     */
    private static boolean isCodeModelTargetValid(Target target) {
        // If the target has no artifacts or filegroups, the target will be get ignored, so mark
        // it valid.
        if (target != null && (target.artifacts == null || target.fileGroups == null)) {
            return true;
        }
        if (target == null || target.name == null || target.buildDirectory == null) {
            return false;
        }

        for (FileGroup fileGroup : target.fileGroups) {
            if (!isCodeModelFileGroupValid(fileGroup)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates if the FileGroup in the code model from Cmake server is valid
     *
     * @return true if the given FileGroup has all the fields that are required by gradle to work as
     *     expected.
     */
    private static boolean isCodeModelFileGroupValid(FileGroup fileGroup) {
        return (fileGroup != null && fileGroup.sources != null && fileGroup.sources.length > 0);
    }
}
