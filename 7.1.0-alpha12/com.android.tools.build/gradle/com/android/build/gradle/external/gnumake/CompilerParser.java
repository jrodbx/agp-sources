/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.external.gnumake;

import com.android.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import joptsimple.OptionParser;

/**
 * Define the C++ compiler parser
 * This contains only the flags that are explicitly needed by all of our sample test.
 */
public class CompilerParser {
    @NonNull
    static final OptionParser PARSER = new OptionParser();

    /*
    These are flags that must be followed by an argument of some kind. These are the forms
    accepted:
        -I include-path
        -I=include-path
        -Iinclude-path
     */
    @NonNull
    static final List<String> WITH_REQUIRED_ARG_FLAGS =
            Arrays.asList(
                    // "B", // -B <directory> Add <directory> to the compiler's search paths
                    "target",
                    "MF", // -MF <file> Write dependency output to the given file
                    "gcc-toolchain",
                    "f", // Flags about the language being compiled (c++ in our case)
                    // -Wa,<options> Pass comma-separated <options> on to the assemble
                    // -Wl,<options> Pass comma-separated <options> on to the linker
                    // -Wp,<options> Pass comma-separated <options> on to the preprocessor
                    "m", // Pass flags to target. For example, -marm
                    "O", // Pass flags to optimization subsystem
                    "D", // D<macro>[=<val>] Define a <macro> with <val> as its value
                    "I", // -I <dir> Add <dir> to the end of the main include path
                    "std", // -std=<standard> Assume that the input sources are for <standard>
                    "isystem", // -isystem <dir> Add <dir> to the start of the system include path
                    "o", // The output file
                    "sysroot", // --sysroot=<directory> Use <directory> as the root directory for
                    // headers
                    // and libraries
                    "l", // -l LIBNAME
                    "L", // -L DIRECTORY
                    "U", // Undefine macro
                    "include", // Includes a .h file
                    "macros",
                    "mllvm" // Options to pass to llvm backend (see
                    // https://issuetracker.google.com/189966589)
                    );

    @NonNull
    static final List<String> WITH_NO_ARG_FLAGS = Arrays.asList(
            "c", // -c Compile and assemble, but do not link
            "g", // -g Generate debug information in default format
            "MMD", // -MMD Like -MD but ignore system header files
            "MP", // -MP Generate phony targets for all headers
            "nostdlib", // -nostdlib Do not look for object files in standard path
            "no-canonical-prefixes", // -no-canonical-prefixes Do not canonicalize paths when
                                     // building relative prefixes to other gcc components
            "shared", // -shared Create a shared library
            "pie" // -pie Create a position independent executable
    );

    static {
        // Also allow flags that we don't recognize for a limited amount of future-proofing.
        PARSER.allowsUnrecognizedOptions();

        // Recognize -W style flags
        PARSER.recognizeAlternativeLongOptions(true);

        for (String flag : WITH_REQUIRED_ARG_FLAGS) {
            PARSER.accepts(flag).withRequiredArg();
        }

        for (String flag : WITH_NO_ARG_FLAGS) {
            PARSER.accepts(flag);
        }
    }

    @SuppressWarnings("SameReturnValue")
    static OptionParser get() {
        return PARSER;
    }
}
