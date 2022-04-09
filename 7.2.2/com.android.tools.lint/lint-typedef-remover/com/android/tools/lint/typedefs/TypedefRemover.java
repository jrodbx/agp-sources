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

package com.android.tools.lint.typedefs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Finds and deletes typedef annotation classes (and also warns if their retention is wrong, such
 * that usages of the annotation embeds data into the .class file.)
 *
 * <p>(Based on the similar class in {@code development/tools/rmtypedefs/})
 */
@SuppressWarnings("SpellCheckingInspection")
public class TypedefRemover {
    private static final String DOT_CLASS = ".class";

    private final boolean mQuiet;
    private final boolean mVerbose;
    private final boolean mDryRun;

    /**
     * Set of typedef classes to be removed. The names are in internal format (e.g. using / to
     * separate packages and $ to separate inner classes).
     */
    private Set<String> mAnnotationNames = Sets.newHashSet();

    /**
     * List of relative paths to the typedef classes to be removed, using / (not File.separator) as
     * the path separator, to match the .zip file paths. This is identical to {@link
     * #mAnnotationNames}, except the file extensions are .class.
     */
    private Set<String> mAnnotationClassFiles = Sets.newHashSet();

    /**
     * List of classes <b>containing</b> removed typedefs. These need to be rewritten to remove
     * .class file references to the inner classes that are being deleted.
     *
     * <p>These are relative paths using / rather than File.separator as the file separator.
     */
    private Set<String> mAnnotationOuterClassFiles = Sets.newHashSet();

    public TypedefRemover(boolean quiet, boolean verbose, boolean dryRun) {
        mQuiet = quiet;
        mVerbose = verbose;
        mDryRun = dryRun;
    }

    public TypedefRemover() {
        this(true, false, false);
    }

    private static void info(@NonNull String message) {
        System.out.println(message);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void warning(String message) {
        System.out.println("Warning: " + message);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private static void error(@NonNull String message) {
        System.err.println("Error: " + message);
    }

    @NonNull
    public TypedefRemover setTypedefFile(@NonNull File file) {
        try {
            for (String line : Files.readLines(file, Charsets.UTF_8)) {
                if (line.startsWith("D ")) {
                    String clz = line.substring(2).trim();
                    addTypeDef(clz);
                }
            }
        } catch (IOException e) {
            error("Could not read " + file + ": " + e.getLocalizedMessage());
        }
        return this;
    }

    /**
     * Filter the given file (given by a path).
     *
     * @param path the path within the jar file
     * @param input the contents of the file
     * @return a stream which provides the content of the file, which may be null (to delete/not
     *     package the file), or the original input stream if the file should be packaged as is, or
     *     possibly a different input stream with the file rewritten
     */
    @Nullable
    public InputStream filter(@NonNull String path, @NonNull InputStream input) {
        if (mAnnotationClassFiles.contains(path)) {
            return null;
        }

        if (!mAnnotationOuterClassFiles.contains(path)) {
            return input;
        }

        // Transform
        try {
            ClassReader reader = new ClassReader(input);
            byte[] rewritten = rewriteOuterClass(reader);
            return new ByteArrayInputStream(rewritten);
        } catch (IOException ioe) {
            error("Could not process " + path + ": " + ioe.getLocalizedMessage());
            return input;
        }
    }

    public void removeFromTypedefFile(@NonNull File classDir, @NonNull File file) {
        removeFromTypedefFile(Collections.singletonList(classDir), file);
    }

    public void removeFromTypedefFile(@NonNull Collection<File> classDirs, @NonNull File file) {
        setTypedefFile(file);
        remove(classDirs, Collections.emptyList());
    }

    public void remove(@NonNull Collection<File> classDir, @NonNull List<String> owners) {
        if (!mQuiet) {
            info("Deleting @IntDef and @StringDef annotation class files");
        }

        // Record typedef annotation names and files
        for (String owner : owners) {
            addTypeDef(owner);
        }

        // Rewrite the .class files for any classes that *contain* typedefs as innerclasses
        rewriteOuterClasses(classDir);

        // Removes the actual .class files for the typedef annotations
        deleteAnnotationClasses(classDir);
    }

    /**
     * Records the given class name (internal name) and class file path as corresponding to a
     * typedef annotation
     */
    private void addTypeDef(String owner) {
        mAnnotationClassFiles.add(owner + DOT_CLASS);
        mAnnotationNames.add(owner);

        int index = owner.lastIndexOf('$');
        if (index != -1) {
            String outer = owner.substring(0, index) + DOT_CLASS;
            if (!mAnnotationOuterClassFiles.contains(outer)) {
                mAnnotationOuterClassFiles.add(outer);
            }
        }
    }

    /**
     * Rewrites the outer classes containing the typedefs such that they no longer refer to the (now
     * removed) typedef annotation inner classes
     */
    private void rewriteOuterClasses(@NonNull Collection<File> classDirs) {
        for (String relative : mAnnotationOuterClassFiles) {
            boolean found = false;
            for (File classDir : classDirs) {
                File file = new File(classDir, relative.replace('/', File.separatorChar));
                if (file.isFile()) {
                    found = true;
                } else {
                    continue;
                }
                byte[] bytes;
                try {
                    bytes = Files.toByteArray(file);
                } catch (IOException e) {
                    error("Could not read " + file + ": " + e.getLocalizedMessage());
                    continue;
                }

                ClassReader reader = new ClassReader(bytes);
                byte[] rewritten = rewriteOuterClass(reader);
                try {
                    Files.write(rewritten, file);
                } catch (IOException e) {
                    error("Could not write " + file + ": " + e.getLocalizedMessage());
                    //noinspection UnnecessaryContinue
                    continue;
                }
            }
            if (!found) {
                warning("Could not find outer class file for " + relative);
            }
        }
    }

    private byte[] rewriteOuterClass(@NonNull ClassReader reader) {
        ClassWriter classWriter = new ClassWriter(Opcodes.ASM7);
        ClassVisitor classVisitor =
                new ClassVisitor(Opcodes.ASM7, classWriter) {
                    @Override
                    public void visitInnerClass(
                            String name, String outerName, String innerName, int access) {
                        if (!mAnnotationNames.contains(name)) {
                            super.visitInnerClass(name, outerName, innerName, access);
                        }
                    }

                    @Override
                    public void visitNestMember(String nestMember) {
                        if (!mAnnotationNames.contains(nestMember)) {
                            super.visitNestMember(nestMember);
                        }
                    }
                };
        reader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    /**
     * Performs the actual deletion (or display, if in dry-run mode) of the typedef annotation files
     */
    private void deleteAnnotationClasses(@NonNull Collection<File> classDirs) {
        for (String relative : mAnnotationClassFiles) {
            boolean found = false;
            for (File classDir : classDirs) {
                File file = new File(classDir, relative.replace('/', File.separatorChar));
                if (file.isFile()) {
                    found = true;
                } else {
                    continue;
                }
                if (mVerbose) {
                    if (mDryRun) {
                        info("Would delete " + file);
                    } else {
                        info("Deleting " + file);
                    }
                }
                if (!mDryRun) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        warning("Could not delete " + file);
                    }
                }
            }
            if (!found) {
                warning("Could not find class file for typedef " + relative);
            }
        }
    }
}
