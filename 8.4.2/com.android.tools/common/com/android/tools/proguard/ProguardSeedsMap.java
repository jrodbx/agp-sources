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
import java.util.HashSet;
import java.util.Set;

/**
 * Proguard seeds map. This is the output of Proguard's {@code -printseeds} option. It represents
 * all the identifiers that were matched by the various {@code -keep} options.
 */
public class ProguardSeedsMap {
    private final Set<String> classes;
    private final Multimap<String, String> methodSpecsByClass;
    private final Multimap<String, String> fieldNamesByClass;

    private ProguardSeedsMap(
            @NonNull Set<String> classes,
            @NonNull Multimap<String, String> methodSpecsByClass,
            @NonNull Multimap<String, String> fieldNamesByClass) {
        this.classes = ImmutableSet.copyOf(classes);
        this.methodSpecsByClass = ImmutableMultimap.copyOf(methodSpecsByClass);
        this.fieldNamesByClass = ImmutableMultimap.copyOf(fieldNamesByClass);
    }

    public boolean hasClass(@NonNull String fqcn) {
        return classes.contains(fqcn);
    }

    public boolean hasMethod(@NonNull String fqcn, @NonNull String methodNameAndParams) {
        return methodSpecsByClass.containsEntry(fqcn, methodNameAndParams);
    }

    public boolean hasField(@NonNull String fqcn, @NonNull String fieldName) {
        return fieldNamesByClass.containsEntry(fqcn, fieldName);
    }

    @NonNull
    public static ProguardSeedsMap parse(@NonNull Path seedsMap) throws IOException {
        return parse(Files.newBufferedReader(seedsMap, Charsets.UTF_8));
    }

    @NonNull
    public static ProguardSeedsMap parse(@NonNull Reader reader) throws IOException {
        Set<String> classes = new HashSet<>();
        Multimap<String, String> methodsByClass = ArrayListMultimap.create();
        Multimap<String, String> fieldsByClass = ArrayListMultimap.create();

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;

            // The seeds file contains lines in one of 3 formats:
            //      fqcn
            //      fqcn: ret-type method(args)
            //      fqcn: type fieldName
            while ((line = br.readLine()) != null) {
                int index = line.indexOf(':');
                if (index < 0) {
                    classes.add(line.trim());
                    continue;
                }

                String fqcn = line.substring(0, index).trim();
                String rest = line.substring(index + 1).trim();
                if (rest.contains("(")) { //it's a method
                    if (rest.indexOf(' ') != -1) { //we don't need the return type
                        rest = rest.substring(rest.indexOf(' ') + 1);
                    }
                    methodsByClass.put(fqcn, rest);
                } else { //it's a field
                    String fieldName =
                            rest.substring(rest.indexOf(' ') + 1); //we don't need the field type
                    fieldsByClass.put(fqcn, fieldName);
                }
            }
        }

        return new ProguardSeedsMap(classes, methodsByClass, fieldsByClass);
    }
}
