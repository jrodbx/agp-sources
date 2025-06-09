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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * General purpose parser for android_build_gradle.json file. This parser is streaming so that the
 * entire Json file is never held in memory all at once.
 */
public class AndroidBuildGradleJsonStreamingParser implements Closeable {

    @NonNull private final JsonReader reader;
    @NonNull private final AndroidBuildGradleJsonStreamingVisitor visitor;

    public AndroidBuildGradleJsonStreamingParser(
            @NonNull JsonReader reader, @NonNull AndroidBuildGradleJsonStreamingVisitor visitor) {
        this.reader = reader;
        this.visitor = visitor;
    }

    /** Main entry point to the streaming parser. */
    public void parse() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "stringTable":
                    parseStringTable();
                    break;
                case "buildFiles":
                    parseBuildFiles();
                    break;
                case "cleanCommandsComponents":
                    parseCleanCommandsComponents();
                    break;
                case "buildTargetsCommandComponents":
                    parseBuildTargetsCommandComponents();
                    break;
                case "cFileExtensions":
                    parseCFileExtensions();
                    break;
                case "cppFileExtensions":
                    parseCppFileExtensions();
                    break;
                case "libraries":
                    parseLibraries();
                    break;
                case "toolchains":
                    parseToolchains();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    /**
     * The purpose of this logic is to read unrecognized Json sections. This should be an unusual
     * situation since this parser is supposed to fully recognized known Json. One situation may be
     * the case where an older gradle plugin finds itself trying to build or clean against a folder
     * created by a more recent gradle plugin.
     */
    private void parseUnknown() throws IOException {
        JsonToken peek = reader.peek();
        switch (peek) {
            case BEGIN_OBJECT:
                parseUnknownObject();
                break;
            case BEGIN_ARRAY:
                parseUnknownArray();
                break;
            case STRING:
                reader.nextString();
                break;
            case NAME:
                reader.nextName();
                break;
            case NULL:
                reader.nextNull();
                break;
            case NUMBER:
                reader.nextString();
                break;
            case BOOLEAN:
                reader.nextBoolean();
                break;
            default:
                // The switch statement is supposed to cover all JsonToken possible right after
                // parsing an earlier object/array/string. It doesn't include END_* tokens because
                // there should be no unmatched BEGIN_* tokens in the rest of this parser.
                throw new RuntimeException(
                        String.format(
                                "Unexpected: Saw Gson token '%s' while parsing "
                                        + "new and unrecognized Json section. ",
                                peek.toString()));
        }
    }

    private void parseUnknownArray() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            parseUnknown();
        }
        reader.endArray();
    }

    private void parseUnknownObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            parseUnknown();
        }
        reader.endObject();
    }

    private void parseLibraryObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "abi":
                    visitor.visitLibraryAbi(reader.nextString());
                    break;
                case "artifactName":
                    visitor.visitLibraryArtifactName(reader.nextString());
                    break;
                case "buildCommandComponents":
                    visitor.visitLibraryBuildCommandComponents(readStringArray());
                    break;
                case "buildType":
                    visitor.visitLibraryBuildType(reader.nextString());
                    break;
                case "output":
                    visitor.visitLibraryOutput(reader.nextString());
                    break;
                case "toolchain":
                    visitor.visitLibraryToolchain(reader.nextString());
                    break;
                case "groupName":
                    visitor.visitLibraryGroupName(reader.nextString());
                    break;
                case "files":
                    parseLibraryFiles();
                    break;
                case "runtimeFiles":
                    parseLibraryRuntimeFiles();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseToolchainObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "cCompilerExecutable":
                    visitor.visitToolchainCCompilerExecutable(reader.nextString());
                    break;
                case "cppCompilerExecutable":
                    visitor.visitToolchainCppCompilerExecutable(reader.nextString());
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseLibraryFileObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "flags":
                    visitor.visitLibraryFileFlags(reader.nextString());
                    break;
                case "flagsOrdinal":
                    visitor.visitLibraryFileFlagsOrdinal(reader.nextInt());
                    break;
                case "src":
                    visitor.visitLibraryFileSrc(reader.nextString());
                    break;
                case "workingDirectory":
                    visitor.visitLibraryFileWorkingDirectory(reader.nextString());
                    break;
                case "workingDirectoryOrdinal":
                    visitor.visitLibraryFileWorkingDirectoryOrdinal(reader.nextInt());
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseStringTable() throws IOException {
        reader.beginObject();
        visitor.beginStringTable();
        while (reader.hasNext()) {
            switch (reader.peek()) {
                case NAME:
                    String index = reader.nextName();
                    String string = reader.nextString();
                    visitor.visitStringTableEntry(Integer.parseInt(index), string);
                    break;
                default:
                    throw new RuntimeException(reader.peek().toString());
            }
        }
        visitor.endStringTable();
        reader.endObject();
    }

    private void parseBuildFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            switch (reader.peek()) {
                case STRING:
                    visitor.visitBuildFile(reader.nextString());
                    break;
                case BEGIN_OBJECT:
                    reader.beginObject();
                    String name = reader.nextName();
                    switch (name) {
                        case "path":
                            visitor.visitBuildFile(reader.nextString());
                            break;
                        default:
                            parseUnknown();
                            break;
                    }
                    reader.endObject();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endArray();
    }

    private void parseCleanCommandsComponents() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            visitor.visitCleanCommandsComponents(readStringArray());
        }
        reader.endArray();
    }

    private void parseBuildTargetsCommandComponents() throws IOException {
        visitor.visitBuildTargetsCommandComponents(readStringArray());
    }

    private List<String> readStringArray() throws IOException {
        ArrayList<String> strings = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            strings.add(reader.nextString());
        }
        reader.endArray();
        return strings;
    }

    private void parseCFileExtensions() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            visitor.visitCFileExtensions(value);
        }
        reader.endArray();
    }

    private void parseCppFileExtensions() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            visitor.visitCppFileExtensions(value);
        }
        reader.endArray();
    }

    private void parseLibraries() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            visitor.beginLibrary(name);
            parseLibraryObject();
            visitor.endLibrary();
        }
        reader.endObject();
    }

    private void parseToolchains() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            visitor.beginToolchain(name);
            parseToolchainObject();
            visitor.endToolchain();
        }
        reader.endObject();
    }

    private void parseLibraryFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            visitor.beginLibraryFile();
            parseLibraryFileObject();
            visitor.endLibraryFile();
        }
        reader.endArray();
    }

    private void parseLibraryRuntimeFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            visitor.visitLibraryRuntimeFile(reader.nextString());
        }
        reader.endArray();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
