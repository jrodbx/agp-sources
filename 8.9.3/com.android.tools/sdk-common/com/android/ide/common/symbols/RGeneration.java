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

package com.android.ide.common.symbols;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Utility class to generate {@code R.java} files.
 */
public class RGeneration {

    private RGeneration() {}

    /**
     * Given a symbol table for the main program (that could be an application, a library or
     * anything that actually generates symbols), and given the symbol tables for all libraries it
     * depends on, generates the {@code R.java} files for each individual library.
     *
     * <p>The {@code R.java} file for the main symbol table is assumed to be generated already.
     *
     * @param main the main symbol file
     * @param libraries the libraries to generate symbols to
     * @param out the output directory where files are generated, must exist and be a directory
     * @param finalIds should final IDs be generated? This should be {@code false} if and only if
     * the artifact being generated is a library or other reusable module and not a final apk
     */
    public static void generateRForLibraries(
            @NonNull SymbolTable main,
            @NonNull Collection<SymbolTable> libraries,
            @NonNull File out,
            boolean finalIds) {
        Preconditions.checkArgument(out.isDirectory(), "!out.iDirectory");

        for (SymbolTable symbolTable : generateLibrarySymbolTablesToWrite(main, main, libraries)) {
            SymbolIo.exportToJava(symbolTable, out, finalIds);
        }
    }

    /**
     * Given a symbol table for the main program (that could be an application, a library or
     * anything that actually generates symbols), and given the symbol tables for all libraries it
     * depends on, generates the {@code R.java} files for each individual library.
     *
     * <p>The {@code R.java} file for the main symbol table is assumed to be generated already.
     *
     * @param allSymbols the symbol table containing the merged resources from all dependencies.
     * @param main the main symbol file
     * @param dependencies the symbol tables from the dependencies
     */
    public static List<SymbolTable> generateAllSymbolTablesToWrite(
            @NonNull SymbolTable allSymbols,
            @NonNull SymbolTable main,
            @NonNull Collection<SymbolTable> dependencies) {
        return ImmutableList.<SymbolTable>builder()
                .add(main)
                .addAll(generateLibrarySymbolTablesToWrite(main, allSymbols, dependencies))
                .build();
    }

    private static List<SymbolTable> generateLibrarySymbolTablesToWrite(
            @NonNull SymbolTable main,
            @NonNull SymbolTable allSymbols,
            @NonNull Collection<SymbolTable> dependencies) {
        /*
         * First we need to make a few changes to the actual symbol tables we are going to write.
         *
         * We don't write symbol tables for libraries that have the same package as
         * the main symbol table because that file is already generated.
         *
         * Then, we must merge symbol tables if they have the same package as symbols for
         * both are read from the same base files.
         */
        Map<String, SymbolTable> toWrite = new HashMap<>();
        for (SymbolTable st : dependencies) {
            if (st.getTablePackage().equals(main.getTablePackage())) {
                continue;
            }

            SymbolTable existing = toWrite.get(st.getTablePackage());
            if (existing != null) {
                toWrite.put(st.getTablePackage(), existing.merge(st));
            } else {
                toWrite.put(st.getTablePackage(), st);
            }
        }

        /*
         * Replace the values of the symbols in the tables to write with the ones in the main
         * symbol table.
         */
        for (String pkg : new HashSet<>(toWrite.keySet())) {
            SymbolTable st = toWrite.get(pkg).withValuesFrom(allSymbols);
            toWrite.put(pkg, st);

            /*
             * Symbols may actually disappear from the library's symbol table. This can happen
             * with library resolution. For example:
             *
             * - Library A version 1 has resource X; it's symbol table will include X and resource
             * X will exist in the library.
             * - Library B version 1 depends on library A version 1; it's symbol table will include
             * X ("inherited" from A), but it won't include resource X since the resource is in
             * library A version 1.
             * - Library A version 2 does not have resource X; it does not exist in A's symbol
             * list nor in its resources.
             * - Library (or application) depends on Library B version 1 *and* Library A version 2.
             *
             * During dependency resolution, when building C we end up with Library A version 2 and
             * Library B, but Library A version 1 is ignored (because version 2 is included). This
             * means that symbol X will exist in Library B's symbol table, but the actual resource
             * does not exist so it won't exist in the main symbol table, which is built from the
             * existing resources.
             *
             * The file we generate for A, in this case, will not include symbol X, although it was
             * in A's original symbol table.
             */
        }

        // Sort the output to make the build output deterministic.
        List<SymbolTable> toWriteList = new ArrayList<>(toWrite.values());
        toWriteList.sort(Comparator.comparing(SymbolTable::getTablePackage));
        return Collections.unmodifiableList(toWriteList);
    }

}
