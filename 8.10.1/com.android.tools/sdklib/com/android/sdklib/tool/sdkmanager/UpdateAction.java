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
package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class UpdateAction extends InstallAction {
    public static final String ACTION_ARG = "--update";

    private UpdateAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    @NonNull
    @Override
    public List<String> getPaths(@NonNull RepoManager mgr) {
        List<String> toUpdate =
                mgr.getPackages().getUpdatedPkgs().stream()
                        .filter(p -> mSettings.includeObsolete() || !p.getRemote().obsolete())
                        .map(p -> p.getRepresentative().getPath())
                        .collect(Collectors.toList());
        PrintStream out = mSettings.getOutputStream();
        out.println();
        out.flush();
        if (toUpdate.isEmpty()) {
            out.println("No updates available");
        } else {
            out.println("Updating:");
            for (String pack : toUpdate) {
                out.println(pack);
            }
        }
        out.flush();
        return toUpdate;
    }

    @Override
    boolean consumeArgument(@NonNull String arg, @NonNull ProgressIndicator progress) {
        return false;
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(ACTION_ARG, UpdateAction::new);
    }
}
