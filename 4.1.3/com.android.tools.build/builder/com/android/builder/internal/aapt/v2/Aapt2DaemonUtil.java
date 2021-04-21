/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.resources.CompileResourceRequest;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;

public class Aapt2DaemonUtil {

    public static final String DAEMON_MODE_COMMAND = "m";

    public static void requestCompile(
            @NonNull Writer writer, @NonNull CompileResourceRequest command) throws IOException {
        request(writer, "c", AaptV2CommandBuilder.makeCompileCommand(command));
    }

    public static void requestLink(@NonNull Writer writer, @NonNull AaptPackageConfig command)
            throws IOException {
        ImmutableList<String> args;
        try {
            args = AaptV2CommandBuilder.makeLinkCommand(command);
        } catch (AaptException e) {
            throw new IOException("Unable to make AAPT link command.", e);
        }
        request(writer, "l", args);
    }

    public static void requestShutdown(@NonNull Writer writer) throws IOException {
        request(writer, "quit", Collections.emptyList());
    }

    private static void request(Writer writer, String command, Iterable<String> args)
            throws IOException {
        writer.write(command);
        writer.write('\n');
        for (String s : args) {
            writer.write(s);
            writer.write('\n');
        }
        // Finish the request
        writer.write('\n');
        writer.write('\n');
        writer.flush();
    }
}
