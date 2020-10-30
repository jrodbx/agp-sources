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

package com.android.builder.desugaring;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.utils.ZipEntryUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Analyzer implemented using ASM visitors to collect information about desugaring dependencies.
 *
 * <p>This analyzer generates {@link DesugaringData} that describes local desugaring dependencies
 * for a single type coming from a .class file or a .jar. To learn which information is relevant for
 * calculating the desugaring dependencies, please see {@link DesugaringData}.
 */
public class DesugaringClassAnalyzer {

    @NonNull
    public static List<DesugaringData> analyze(@NonNull Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return analyzeDir(path);
        } else if (Files.isRegularFile(path)) {
            if (path.toString().endsWith(SdkConstants.DOT_JAR)) {
                return analyzeJar(path);
            } else if (ClassFileInput.CLASS_MATCHER.test(path.toString())) {
                return ImmutableList.of(analyzeClass(path));
            } else {
                return ImmutableList.of();
            }
        } else {
            throw new IOException("Unable to process " + path.toString());
        }
    }

    @NonNull
    private static List<DesugaringData> analyzeJar(@NonNull Path jar) throws IOException {
        Preconditions.checkArgument(
                jar.toString().endsWith(SdkConstants.DOT_JAR) && Files.isRegularFile(jar),
                "Not a .jar file: %s",
                jar.toString());

        List<DesugaringData> data;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            data = new ArrayList<>(zip.size());
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!ZipEntryUtils.isValidZipEntryName(zipEntry)) {
                    continue;
                }
                if (!ClassFileInput.CLASS_MATCHER.test(zipEntry.getName())) {
                    continue;
                }
                try (BufferedInputStream inputStream =
                        new BufferedInputStream(zip.getInputStream(zipEntry))) {
                    data.add(analyze(jar, inputStream));
                }
            }
        }
        return data;
    }

    @NonNull
    private static List<DesugaringData> analyzeDir(@NonNull Path dir) throws IOException {
        Preconditions.checkArgument(Files.isDirectory(dir), "Not a directory: %s", dir.toString());

        List<DesugaringData> data = new LinkedList<>();
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Path relative = dir.relativize(file);
                        if (ClassFileInput.CLASS_MATCHER.test(relative.toString())) {
                            data.add(analyzeClass(file));
                        }

                        return super.visitFile(file, attrs);
                    }
                });
        return data;
    }

    @NonNull
    private static DesugaringData analyzeClass(@NonNull Path classFile) throws IOException {
        Preconditions.checkArgument(
                classFile.toString().endsWith(SdkConstants.DOT_CLASS)
                        && Files.isRegularFile(classFile),
                "Not a .class file: %s",
                classFile.toString());

        try (InputStream is = new BufferedInputStream(Files.newInputStream(classFile))) {
            return analyze(classFile, is);
        }
    }

    @NonNull
    public static DesugaringData forRemoved(@NonNull Path path) {
        Preconditions.checkArgument(!Files.exists(path), "%s exists.", path.toString());
        return new DesugaringData(path);
    }

    @NonNull
    @VisibleForTesting
    static DesugaringData analyze(@NonNull Path path, @NonNull InputStream is) throws IOException {
        ClassReader reader = new ClassReader(is);
        Visitor visitor = new Visitor(path);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new DesugaringData(visitor.getPath(), visitor.getType(), visitor.getDependencies());
    }

    private static class Visitor extends ClassVisitor {
        @NonNull private final Path path;
        private String type;
        @NonNull private final Set<String> dependencies = Sets.newHashSet();

        public Visitor(@NonNull Path path) {
            super(Opcodes.ASM7);
            this.path = path;
        }

        @NonNull
        public Path getPath() {
            return path;
        }

        @NonNull
        public String getType() {
            return Preconditions.checkNotNull(type, "visit() not invoked");
        }

        @NonNull
        public Set<String> getDependencies() {
            return dependencies;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {
            type = name;
            if (superName != null) {
                // is is null for java/lang/Object
                dependencies.add(superName);
            }
            if (interfaces != null) {
                Collections.addAll(dependencies, interfaces);
            }
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor =
                    super.visitMethod(access, name, desc, signature, exceptions);

            return new LambdaSeeker(dependencies, methodVisitor);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            // Add dependency to support nest-based access control - b/130578767.
            // Invoked only for local/anonymous classes with EnclosingMethod attribute -
            // https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.7
            if (owner != null) {
                dependencies.add(owner);
            }
            super.visitOuterClass(owner, name, desc);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // Need inner class handling because of the nest-based access control - b/130578767.
            if (type.equals(outerName)) {
                // Name denotes an inner class enclosed by this type => depend on it.
                dependencies.add(name);
            } else if (type.equals(name)) {
                // We are in an inner class, and outerName contains where it was declared if it is
                // not null => depend on it. OuterName will be null for local/anonymous classes
                if (outerName != null) {
                    dependencies.add(outerName);
                }
            } else if (outerName == null) {
                // Name is a non-member class => depend on it.
                dependencies.add(name);
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }
    }

    private static class LambdaSeeker extends MethodVisitor {
        @NonNull private final Set<String> dependencies;

        public LambdaSeeker(@NonNull Set<String> dependencies, @NonNull MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
            this.dependencies = dependencies;
        }

        @Override
        public void visitInvokeDynamicInsn(
                String name, String desc, Handle bsm, Object... bsmArgs) {
            Type methodType = Type.getMethodType(desc);
            String internalName = methodType.getReturnType().getInternalName();
            dependencies.add(internalName);

            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        }
    }
}
