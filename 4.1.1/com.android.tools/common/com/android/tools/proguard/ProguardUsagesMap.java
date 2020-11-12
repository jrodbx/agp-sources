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
package com.android.tools.proguard;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proguard dead code map. This is output from Proguard with the {@code -printusage} option, and
 * represents the dead code that was removed by Proguard.
 */
public class ProguardUsagesMap {

    private final Set<String> classes;
    private final Multimap<String, String> methodsByClass;
    private final Multimap<String, String> fieldsByClass;

    private static ImmutableSet<String> modifiers =
            ImmutableSet.of(
                    "abstract",
                    "final",
                    "native",
                    "private",
                    "protected",
                    "public",
                    "strictfp",
                    "static",
                    "synchronized",
                    "transient",
                    "volatile");

    private ProguardUsagesMap(
            @NonNull Set<String> classes,
            @NonNull Multimap<String, String> methodsByClass,
            @NonNull Multimap<String, String> fieldsByClass) {
        this.classes = ImmutableSet.copyOf(classes);
        this.methodsByClass = ImmutableMultimap.copyOf(methodsByClass);
        this.fieldsByClass = ImmutableMultimap.copyOf(fieldsByClass);
    }

    @NonNull
    public Collection<String> getClasses() {
        return classes;
    }

    @NonNull
    public Multimap<String, String> getMethodsByClass() {
        return methodsByClass;
    }

    @NonNull
    public Multimap<String, String> getFieldsByClass() {
        return fieldsByClass;
    }

    public boolean hasClass(@NonNull String fqcn) {
        return classes.contains(fqcn);
    }

    public boolean hasMethod(@NonNull String fqcn, @NonNull String methodSig) {
        return methodsByClass.containsEntry(fqcn, methodSig);
    }

    public boolean hasField(@NonNull String fqcn, @NonNull String fieldName) {
        return fieldsByClass.containsEntry(fqcn, fieldName);
    }

    @NonNull
    public static ProguardUsagesMap parse(@NonNull Path usageFile) throws IOException {
        return parse(Files.newBufferedReader(usageFile, Charsets.UTF_8));
    }

    @NonNull
    public static ProguardUsagesMap parse(@NonNull Reader reader) throws IOException {
        Set<String> classes = new HashSet<>();
        Multimap<String, String> methods = ArrayListMultimap.create();
        Multimap<String, String> fields = ArrayListMultimap.create();

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            String currentClass = null;

            while ((line = br.readLine()) != null) {
                String trimmedLine = line.trim();
                if (line.isEmpty() || trimmedLine.charAt(0) == '#') {
                    continue;
                }
                if (!Character.isWhitespace(line.charAt(0))) {
                    if (line.endsWith(":")) {
                        // indicates that the lines following this line contain methods and fields
                        // that were removed
                        currentClass = line.substring(0, line.length() - 1);
                    } else {
                        // this line contains the fqcn of a class that was removed
                        classes.add(line);
                        currentClass = null;
                    }
                } else {
                    line = trimmedLine;

                    if (currentClass == null) {
                        String msg =
                                "Unexpected format for proguard usages map. Encountered method or "
                                        + "field with unknown class at line: "
                                        + line;
                        throw new IOException(msg);
                    }

                    // skip past any line number information
                    // when present, it is of the form 12:34:public void..
                    line = line.substring(line.lastIndexOf(':') + 1);

                    if (line.contains("(")) {
                        methods.put(currentClass, getMethodSpec(line));
                    } else {
                        fields.put(currentClass, getFieldName(line));
                    }
                }
            }
        }

        return new ProguardUsagesMap(classes, methods, fields);
    }

    @NonNull
    private static String getFieldName(@NonNull String line) throws IOException {
        //the fields can have any number of modifiers and always have a type and name:
        //org.type.MyType fieldName
        //private int fieldName
        //public static org.type.MyType fieldName

        //get index of fieldName
        int i = line.lastIndexOf(' ');
        //get index of field type
        int j = line.lastIndexOf(' ', i - 1);
        //if there are no modifiers, there is no ' ' (space) before field type, assume 0
        i = (j >= 0) ? j : 0;
        if (i < 0 || i == line.length() - 1) {
            String message = "Unexpected field specification in proguard usages map: " + line;
            throw new IOException(message);
        }
        return line.substring(i + 1);
    }

    @NonNull
    private static String getMethodSpec(@NonNull String line) {
        return Arrays.stream(line.split(" "))
                .filter(s -> !modifiers.contains(s))
                .collect(Collectors.joining(" "))
                .trim();
    }
}
