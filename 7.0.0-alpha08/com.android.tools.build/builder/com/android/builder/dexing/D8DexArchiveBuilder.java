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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.Message;
import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

final class D8DexArchiveBuilder extends DexArchiveBuilder {

    private static final String INVOKE_CUSTOM =
            "Invoke-customs are only supported starting with Android O";

    private static final String DEFAULT_INTERFACE_METHOD =
            "Default interface methods are only supported starting with Android N (--min-api 24)";

    private static final String STATIC_INTERFACE_METHOD =
            "Static interface methods are only supported starting with Android N (--min-api 24)";

    @NonNull private DexParameters dexParams;

    public D8DexArchiveBuilder(@NonNull DexParameters dexParams) {
        this.dexParams = dexParams;
    }

    @Override
    public void convert(
            @NonNull Stream<ClassFileEntry> input,
            @NonNull Path output,
            @Nullable DependencyGraphUpdater<File> desugarGraphUpdater)
            throws DexArchiveBuilderException {
        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
        try {

            D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
            AtomicInteger entryCount = new AtomicInteger();
            input.forEach(
                    entry -> {
                        builder.addClassProgramData(
                                readAllBytes(entry), D8DiagnosticsHandler.getOrigin(entry));
                        entryCount.incrementAndGet();
                    });
            if (entryCount.get() == 0) {
                // nothing to do here, just return
                return;
            }

            builder.setMode(
                            dexParams.getDebuggable()
                                    ? CompilationMode.DEBUG
                                    : CompilationMode.RELEASE)
                    .setMinApiLevel(dexParams.getMinSdkVersion())
                    .setIntermediate(true)
                    .setOutput(
                            output,
                            dexParams.getDexPerClass()
                                    ? OutputMode.DexFilePerClassFile
                                    : OutputMode.DexIndexed)
                    .setIncludeClassesChecksum(dexParams.getDebuggable());

            if (dexParams.getDebuggable()) {
                builder.addAssertionsConfiguration(
                        AssertionsConfiguration.Builder::enableAllAssertions);
            }

            if (dexParams.getWithDesugaring()) {
                builder.addLibraryResourceProvider(
                        dexParams.getDesugarBootclasspath().getOrderedProvider());
                builder.addClasspathResourceProvider(
                        dexParams.getDesugarClasspath().getOrderedProvider());

                if (dexParams.getCoreLibDesugarConfig() != null) {
                    builder.addSpecialLibraryConfiguration(dexParams.getCoreLibDesugarConfig());
                    if (dexParams.getCoreLibDesugarOutputKeepRuleFile() != null) {
                        builder.setDesugaredLibraryKeepRuleConsumer(
                                new FileConsumer(
                                        dexParams.getCoreLibDesugarOutputKeepRuleFile().toPath()));
                    }
                }
                if (desugarGraphUpdater != null) {
                    builder.setDesugarGraphConsumer(
                            new D8DesugarGraphConsumerAdapter(desugarGraphUpdater));
                }
            } else {
                builder.setDisableDesugaring(true);
            }

            D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
        }
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull ClassFileEntry entry) {
        try {
            return entry.readAllBytes();
        } catch (IOException ex) {
            throw new DexArchiveBuilderException(ex);
        }
    }

    @NonNull
    private static DexArchiveBuilderException getExceptionToRethrow(
            @NonNull Throwable t, D8DiagnosticsHandler d8DiagnosticsHandler) {
        StringBuilder msg = new StringBuilder();
        msg.append("Error while dexing.");
        for (String hint : d8DiagnosticsHandler.getPendingHints()) {
            msg.append(System.lineSeparator());
            msg.append(hint);
        }

        return new DexArchiveBuilderException(msg.toString(), t);
    }

    private static String getEnableDesugaringHint(int minSdkVersion) {
        return "The dependency contains Java 8 bytecode. Please enable desugaring by "
                + "adding the following to build.gradle\n"
                + "android {\n"
                + "    compileOptions {\n"
                + "        sourceCompatibility 1.8\n"
                + "        targetCompatibility 1.8\n"
                + "    }\n"
                + "}\n"
                + "See https://developer.android.com/studio/write/java8-support.html for "
                + "details. Alternatively, increase the minSdkVersion to "
                + minSdkVersion
                + " or above.\n";
    }

    private class InterceptingDiagnosticsHandler extends D8DiagnosticsHandler {
        public InterceptingDiagnosticsHandler() {
            super(D8DexArchiveBuilder.this.dexParams.getMessageReceiver());
        }

        @Override
        protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {

            if (diagnostic.getDiagnosticMessage().startsWith(INVOKE_CUSTOM)) {
                addHint(getEnableDesugaringHint(26));
            }

            if (diagnostic.getDiagnosticMessage().startsWith(DEFAULT_INTERFACE_METHOD)) {
                addHint(getEnableDesugaringHint(24));
            }

            if (diagnostic.getDiagnosticMessage().startsWith(STATIC_INTERFACE_METHOD)) {
                addHint(getEnableDesugaringHint(24));
            }

            return super.convertToMessage(kind, diagnostic);
        }
    }
}
