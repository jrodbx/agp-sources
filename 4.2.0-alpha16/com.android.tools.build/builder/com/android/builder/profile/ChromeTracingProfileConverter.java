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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.utils.PathUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonWriter;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Utility for converting profile data to chrome tracing format.
 *
 * Produced files can be opened in chrome://tracing
 */
public class ChromeTracingProfileConverter {

    /**
     * Name of directory containing extra Chrome trace files that should be merged into the final
     * JSON trace file. The content of this directory should be some gzipped JSON files following
     * the Chrome trace format
     * (https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU).
     *
     * <p>Other parts of AGP can output trace files into "PROFILE_DIRECTORY/extra_chrome_trace" (see
     * https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/profile/ProfilerInitializer.java
     * ) and they will all be picked up by {@link ChromeTracingProfileConverter}. One should use the
     * helper data class {@link ChromeTraceJson} to generate such a JSON file to ensure
     * compatibility with the merger.
     *
     * <p>The directory is deleted at end of every build cycle to save disk space and prevent stale
     * result to tamper future builds.
     */
    public static final String EXTRA_CHROME_TRACE_DIRECTORY = "extra_chrome_trace";

    public static void main(String[] args)  {
        try {
            if (args.length != 1) {
                throw new AbnormalExitException("Please supply exactly one argument.");
            }
            try {
                toJson(args[0]);
            } catch (IOException e) {
                throw new AbnormalExitException(e);
            }
        } catch (AbnormalExitException e) {
            System.err.println(e.getMessage());
            if (e.getCause() != null) {
                System.err.println(Throwables.getStackTraceAsString(e));
            }
            System.err.println();
            System.err.println("Usage:  ChromeTracingProfileConverter <proto_file>");
            System.err.println("        ChromeTracingProfileConverter <directory>");
            System.err.println();
            System.err.println("Given a proto file, outputs a corresponding json file");
            System.err.println("in the same directory that can be opened in chrome://tracing.");
            System.err.println();
            System.err.println("Given a directory, walks the directory and converts all the");
            System.err.println("files not ending in '.json'.");
            System.exit(1);
        }
    }

    private static void toJson(@NonNull String pathString) throws IOException {
        Path path = Paths.get(pathString);
        if (Files.isRegularFile(path)) {
            toJson(path);
            System.out.format(Locale.US, "Converted %1$s%n", path.getFileName());
        } else if (Files.isDirectory(path)) {
            Files.walkFileTree(
                    path,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            if (file.getFileName().toString().endsWith(".rawproto")) {
                                toJson(file);
                                System.out.format(
                                        Locale.US, "Converted %1$s\n", path.relativize(file));
                                return FileVisitResult.CONTINUE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } else {
            throw new AbnormalExitException(
                    String.format(
                            Locale.US,
                            "Error: Argument is neither a file nor a directory: '%1$s'",
                            pathString));
        }
    }

    public static Path getJsonOutFile(Path protoFile) {
        String fileName = protoFile.getFileName().toString();
        if (fileName.endsWith(".rawproto")) {
            fileName = fileName.substring(0, fileName.length() - ".rawproto".length());
        }
        return protoFile.getParent().resolve(fileName + ".json.gz");
    }

    public static void toJson(@NonNull Path protoFile) throws IOException, AbnormalExitException {
        GradleBuildProfile profile;
        try {
            profile = GradleBuildProfile
                    .parseFrom(Files.readAllBytes(protoFile));
        } catch (InvalidProtocolBufferException e) {
            throw new AbnormalExitException(
                    String.format(
                            Locale.US,
                            "Could not parse proto file: %1$s '%2$s'",
                            e.getMessage(),
                            protoFile));
        }
        Path out = getJsonOutFile(protoFile);

        Map<Long, ProjectHolder> projects =
                profile.getProjectList().stream()
                        .collect(Collectors.toMap(GradleBuildProject::getId, ProjectHolder::new));

        try (JsonWriter writer =
                new JsonWriter(
                        new OutputStreamWriter(
                                new GZIPOutputStream(Files.newOutputStream(out)),
                                StandardCharsets.UTF_8))) {
            writer.beginObject();
            writer.name("traceEvents");
            writer.beginArray();
            long previousTime = 0;
            for (GradleBuildMemorySample memorySample : profile.getMemorySampleList()) {
                long timestamp = memorySample.getTimestamp() * 1000;
                if (timestamp == previousTime) {
                    timestamp += 1;
                }
                previousTime = timestamp;
                writer.beginObject()
                        .name("pid").value(0)
                        .name("ph").value("i")
                        .name("name").value("Memory sample")
                        .name("ts").value(timestamp)
                        .name("args");
                {
                    writer.beginObject();
                    writer.name("JVM stats").value(memorySample.getJavaProcessStats().toString());
                    writer.endObject();
                }
                writer.endObject();
            }

            Map<Long, String> taskList =
                    profile.getSpanList()
                            .stream()
                            .filter(it -> it.getParentId() == 0)
                            .collect(
                                    Collectors.toMap(
                                            GradleBuildProfileSpan::getId,
                                            ChromeTracingProfileConverter::getSpanName));
            for (GradleBuildProfileSpan span : profile.getSpanList()) {
                writer
                        .beginObject()
                        .name("pid").value(1)
                        .name("tid").value(span.getThreadId())
                        .name("id").value(span.getId());
                ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();

                args.put("span_id", span.getId());
                if (span.getParentId() != 0) {
                    args.put("parent_span_id", span.getParentId());
                } else {
                    ProjectHolder projectHolder = projects.get(span.getProject());
                    if (projectHolder != null) {
                        args.put("project", projectHolder.project);
                        if (span.getVariant() != 0) {
                            GradleBuildVariant variant =
                                    projectHolder.variants.get(span.getVariant());
                            if (variant != null) {
                                args.put("variant", variant);
                            }
                        }
                    }
                }
                if (span.getParentId() != 0) {
                    writer.name("name")
                            .value(getChildSpanName(span, taskList.get(span.getParentId())));
                } else {
                    writer.name("name").value(getSpanName(span));
                }
                switch (span.getType()) {
                    case TASK_EXECUTION:
                        args.put("task", span.getTask());
                        break;
                    case TASK_TRANSFORM:
                        args.put("transform", span.getTransform());
                        break;
                    case TASK_TRANSFORM_PREPARATION:
                        args.put("transform prep", span.getTransform());
                        break;
                    case WORKER_EXECUTION:
                        args.put("type", "worker");
                        break;
                    case THREAD_EXECUTION:
                        args.put("type", "thread");
                        break;
                    case ARTIFACT_TRANSFORM:
                        args.put("type", "artifact");
                        break;
                }

                writer.name("args").beginObject();
                for (Map.Entry<String, Object> entry : args.build().entrySet()) {
                    writer.name(entry.getKey()).value(entry.getValue().toString());
                }
                writer.endObject();
                long duration = span.getDurationInMs() * 1000;
                writer
                        .name("ph").value("X")
                        .name("ts").value(span.getStartTimeInMs() * 1000)
                        .name("dur").value(duration == 0 ? 100 : duration)
                        .endObject();
            }
            Path extraChromeTraceDir = protoFile.getParent().resolve(EXTRA_CHROME_TRACE_DIRECTORY);
            MergeExtraTraceFilesKt.mergeExtraTraceFiles(2, writer, extraChromeTraceDir);
            PathUtils.deleteRecursivelyIfExists(extraChromeTraceDir);
            writer.endArray();
            writeStackFrames(writer, profile);
            writer.endObject();
        }
    }

    static String getChildSpanName(GradleBuildProfileSpan span, String parentName) {
        switch (span.getType()) {
            case WORKER_EXECUTION:
            case THREAD_EXECUTION:
                return parentName;
            default:
                return pretty(span.getType());
        }
    }

    static String getSpanName(GradleBuildProfileSpan span) {
        switch (span.getType()) {
            case TASK_EXECUTION:
                return taskName(span);
            case TASK_TRANSFORM:
                return transformName(span);
            case TASK_TRANSFORM_PREPARATION:
                return "Prep for " + transformName(span);
            default:
                return pretty(span.getType());
        }
    }

    static void writeStackFrames(JsonWriter writer, GradleBuildProfile profile) throws IOException {
        writer.name("stackFrames");
        writer.beginObject();
        for (GradleBuildProfileSpan span : profile.getSpanList()) {
            writer.name(String.valueOf(span.getId()));
            writer.beginObject().name("name").value(getSpanName(span));
            if (span.getParentId() != 0) {
                writer.name("parent").value(String.valueOf(span.getParentId()));
            }
            writer.endObject();
        }
        writer.endObject();
    }

    static final class ProjectHolder {
        final GradleBuildProject project;
        final Map<Long, GradleBuildVariant> variants;

        ProjectHolder(@NonNull GradleBuildProject project) {
            this.project = project.toBuilder().clearVariant().build();
            variants =
                project.getVariantList().stream()
                    .collect(Collectors.toMap(GradleBuildVariant::getId, Function.identity()));
        }
    }

    @NonNull
    private static String taskName(@NonNull GradleBuildProfileSpan span) {
        return pretty(GradleTaskExecutionType.forNumber(span.getTask().getType()));
    }

    @NonNull
    private static String transformName(@NonNull GradleBuildProfileSpan span) {
        return span.getTransform().getTransformClassName();
    }

    private static String pretty(Enum theEnum) {
        return theEnum.toString().toLowerCase(Locale.US).replace('_', ' ');
    }

    private static final class AbnormalExitException extends RuntimeException {
        AbnormalExitException(@NonNull String text) {
            super(text);
        }

        AbnormalExitException(@NonNull Throwable throwable) {
            super(throwable);
        }
    }

}
