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

package com.android.ide.common.blame.parser;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DexParser implements PatternAwareOutputParser {

    public static final String DX_UNEXPECTED_EXCEPTION = "UNEXPECTED TOP-LEVEL EXCEPTION:";

    public static final String ERROR_INVOKE_DYNAMIC =
            "invalid opcode ba - invokedynamic requires --min-sdk-version >= 26";

    public static final String DEX_LIMIT_EXCEEDED_ERROR =
            "The number of method references in a .dex file cannot exceed 64K.\n"
                    + "Learn how to resolve this issue at "
                    + "https://developer.android.com/tools/building/multidex.html";

    static final String DEX_TOOL_NAME = "Dex";

    static final String COULD_NOT_CONVERT_BYTECODE_TO_DEX =
            "Error converting bytecode to dex:\nCause: %s";

    static final String INVALID_BYTE_CODE_VERSION = "Dex cannot parse version %1$d byte code.\n"
            + "This is caused by library dependencies that have been compiled using Java 8 "
            + "or above.\n"
            + "If you are using the 'java' gradle plugin in a library submodule add \n"
            + "targetCompatibility = '1.7'\n"
            + "sourceCompatibility = '1.7'\n"
            + "to that submodule's build.gradle file.";

    private static final Pattern INVALID_BYTE_CODE_VERSION_EXCEPTION_PATTERN = Pattern.compile(
            "com.android.dx.cf.iface.ParseException: bad class file magic \\(cafebabe\\) or version \\((\\d+)\\.\\d+\\).*");

    private static final Pattern UNSUPPORTED_CLASS_FILE_VERSION_PATTERN = Pattern.compile(
            "unsupported class file version (\\d+)\\.\\d+");

    @Override
    public boolean parse(@NonNull String line, @NonNull OutputLineReader reader,
            @NonNull List<Message> messages, @NonNull ILogger logger)
            throws ParsingFailedException {
        if (line.startsWith("processing ") && line.endsWith("...")) {
            // There is one such line for every class compiled, i.e. a lot of them. Log at debug
            // level, otherwise --info becomes unusable.
            logger.verbose(line);
            return true;
        }

        if (line.startsWith("writing ") && line.endsWith("size 0...")) {
            // There is one such line for every directory in the input jars. Log at debug level.
            logger.verbose(line);
            return true;
        }

        if (line.startsWith("ignored resource ") && line.endsWith("/")) {
            // There is one such line for every directory in the input jars. Log at debug level.
            logger.verbose(line);
            return true;
        }


        if (line.startsWith("warning: Ignoring InnerClasses attribute")) {
            StringBuilder original1 = new StringBuilder(line).append('\n');
            String nextLine = reader.readLine();
            while (!Strings.isNullOrEmpty(nextLine)) {
                original1.append(nextLine).append('\n');
                if (nextLine.equals("indicate that it is *not* an inner class.")) {
                    break;
                }
                nextLine = reader.readLine();
            }
            messages.add(
                    new Message(
                            Message.Kind.WARNING,
                            original1.toString(),
                            original1.toString(),
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        }

        if (line.startsWith("trouble writing output: Too many method references")) {
            StringBuilder original1 = new StringBuilder(line).append('\n');
            String nextLine = reader.readLine();
            while (!Strings.isNullOrEmpty(nextLine)) {
                original1.append(nextLine).append('\n');
                nextLine = reader.readLine();
            }
            messages.add(
                    new Message(
                            Message.Kind.ERROR,
                            DEX_LIMIT_EXCEEDED_ERROR,
                            original1.toString(),
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        }
        if (line.equals("PARSE ERROR:")) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return false;
            }
            StringBuilder locationsBuilder = new StringBuilder();
            consumeMatchingLines(reader, nextLine -> nextLine.startsWith("..."), locationsBuilder);
            String locations = locationsBuilder.toString();
            String originalMessage = "PARSE ERROR:\n" + firstLine + "\n" + locations;
            String cause = originalMessage;

            if (firstLine.startsWith("unsupported class file version ")) {
                Matcher matcher = UNSUPPORTED_CLASS_FILE_VERSION_PATTERN.matcher(firstLine);
                if (matcher.find()) {
                    int bytecodeVersion = Integer.valueOf(matcher.group(1));
                    cause = String.format(INVALID_BYTE_CODE_VERSION, bytecodeVersion)
                            + "\n" + locations.trim();
                }
            }
            messages.add(
                    new Message(
                            Message.Kind.ERROR,
                            String.format(COULD_NOT_CONVERT_BYTECODE_TO_DEX, cause),
                            originalMessage,
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        }

        if (!line.equals(DX_UNEXPECTED_EXCEPTION)) {
            return false;
        }
        StringBuilder original = new StringBuilder(line).append('\n');
        String exception = reader.readLine();
        if (exception == null) {
            reader.pushBack();
            return false;
        }
        original.append(exception).append('\n');
        consumeStacktrace(reader, original);
        String exceptionWithStacktrace = original.toString();

        if (exception.startsWith(
                "com.android.dex.DexIndexOverflowException: method ID not in [0, 0xffff]: ")) {
            messages.add(
                    new Message(
                            Message.Kind.ERROR,
                            DEX_LIMIT_EXCEEDED_ERROR,
                            exceptionWithStacktrace,
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        } else if (exception.startsWith(
                "com.android.dx.cf.code.SimException: " + ERROR_INVOKE_DYNAMIC)) {
            messages.add(
                    new Message(
                            Message.Kind.ERROR,
                            getEnableDesugaringHint(26),
                            exceptionWithStacktrace,
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        } else {
            String cause = exception;
            Matcher invalidByteCodeVersion = INVALID_BYTE_CODE_VERSION_EXCEPTION_PATTERN.matcher(
                    exceptionWithStacktrace);
            if (invalidByteCodeVersion.find()) {
                int bytecodeVersion = Integer.valueOf(invalidByteCodeVersion.group(1), 16);
                cause = String.format(INVALID_BYTE_CODE_VERSION, bytecodeVersion);
            }
            messages.add(
                    new Message(
                            Message.Kind.ERROR,
                            String.format(COULD_NOT_CONVERT_BYTECODE_TO_DEX, cause),
                            exceptionWithStacktrace,
                            DEX_TOOL_NAME,
                            ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        }
    }

    public static String getEnableDesugaringHint(int minSdkVersion) {
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

    private static void consumeStacktrace(
            @NonNull OutputLineReader reader,
            @NonNull StringBuilder out) {
        consumeMatchingLines(
                reader,
                line -> line.startsWith("\t") || line.startsWith("Caused by: "),
                out);
    }

    private static void consumeMatchingLines(
            @NonNull OutputLineReader reader,
            @NonNull Predicate<String> linePredicate,
            @NonNull StringBuilder out) {
        String nextLine = reader.readLine();
        while (nextLine != null && linePredicate.test(nextLine)) {
            out.append(nextLine).append('\n');
            nextLine = reader.readLine();
        }
        //noinspection VariableNotUsedInsideIf
        if (nextLine != null) {
            reader.pushBack();
        }
    }
}
