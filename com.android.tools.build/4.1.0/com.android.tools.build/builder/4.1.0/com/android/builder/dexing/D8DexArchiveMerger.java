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

package com.android.builder.dexing;

import static com.android.builder.dexing.D8ErrorMessagesKt.ERROR_DUPLICATE_HELP_PAGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.parser.DexParser;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

final class D8DexArchiveMerger implements DexArchiveMerger {

    @NonNull
    private static final Logger LOGGER = Logger.getLogger(D8DexArchiveMerger.class.getName());

    private static final String ERROR_MULTIDEX =
            "Cannot fit requested classes in a single dex file";

    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final ForkJoinPool forkJoinPool;
    private volatile boolean hintForMultidex = false;

    public D8DexArchiveMerger(
            @Nonnull MessageReceiver messageReceiver,
            int minSdkVersion,
            @NonNull CompilationMode compilationMode,
            @NonNull ForkJoinPool forkJoinPool) {
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = compilationMode;
        this.messageReceiver = messageReceiver;
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public void mergeDexArchives(
            @NonNull Iterator<Path> inputs,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException {
        List<Path> inputsList = Lists.newArrayList(inputs);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(
                    Level.INFO,
                    "Merging to '"
                            + outputDir.toAbsolutePath().toString()
                            + "' with D8 from "
                            + inputsList
                                    .stream()
                                    .map(path -> path.toAbsolutePath().toString())
                                    .collect(Collectors.joining(", ")));
        }
        if (inputsList.isEmpty()) {
            return;
        }

        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
        D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
        builder.setDisableDesugaring(true);
        builder.setIncludeClassesChecksum(compilationMode == CompilationMode.DEBUG);

        for (Path input : inputsList) {
            try (DexArchive archive = DexArchives.fromInput(input)) {
                for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                    builder.addDexProgramData(
                            dexArchiveEntry.getDexFileContent(),
                            D8DiagnosticsHandler.getOrigin(dexArchiveEntry));
                }
            } catch (IOException e) {
                throw getExceptionToRethrow(e, d8DiagnosticsHandler);
            }
        }
        try {
            if (mainDexClasses != null) {
                builder.addMainDexListFiles(mainDexClasses);
            }
            builder.setMinApiLevel(minSdkVersion)
                    .setMode(compilationMode)
                    .setOutput(outputDir, OutputMode.DexIndexed)
                    .setDisableDesugaring(true)
                    .setIntermediate(false);
            D8.run(builder.build(), forkJoinPool);
        } catch (CompilationFailedException e) {
            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
        }
    }

    @NonNull
    private DexArchiveMergerException getExceptionToRethrow(
            @NonNull Throwable t,
            D8DiagnosticsHandler d8DiagnosticsHandler) {
        StringBuilder msg = new StringBuilder("Error while merging dex archives: ");
        for (String hint : d8DiagnosticsHandler.getPendingHints()) {
            msg.append(System.lineSeparator());
            msg.append(hint);
        }
        return new DexArchiveMergerException(msg.toString(), t);
    }


    private class InterceptingDiagnosticsHandler extends D8DiagnosticsHandler {
        public InterceptingDiagnosticsHandler() {
            super(D8DexArchiveMerger.this.messageReceiver);
        }

        @Override
        protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {

            if (diagnostic.getDiagnosticMessage().startsWith(ERROR_MULTIDEX)) {
                addHint(DexParser.DEX_LIMIT_EXCEEDED_ERROR);
            }

            if (diagnostic instanceof DuplicateTypesDiagnostic) {
                addHint(diagnostic.getDiagnosticMessage());
                addHint(ERROR_DUPLICATE_HELP_PAGE);
            }

            return super.convertToMessage(kind, diagnostic);
        }
    }

}
