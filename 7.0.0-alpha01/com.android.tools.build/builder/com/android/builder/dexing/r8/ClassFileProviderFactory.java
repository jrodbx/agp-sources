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

package com.android.builder.dexing.r8;

import com.android.annotations.NonNull;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DirectoryClassFileProvider;
import com.android.tools.r8.ProgramResource;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Provides {@link ClassFileResourceProvider} suitable for D8/R8 classpath and bootclasspath
 * entries. Some of those may be shared.
 */
public class ClassFileProviderFactory implements Closeable {

    /**
     * Ordered class file provider. When looking for a descriptor, it searches from the first, until
     * the last specified provider.
     */
    private static class OrderedClassFileResourceProvider implements ClassFileResourceProvider {
        private final Supplier<Map<String, ClassFileResourceProvider>> descriptors;

        OrderedClassFileResourceProvider(List<ClassFileResourceProvider> providers) {
            this.descriptors =
                    Suppliers.memoize(
                            () -> {
                                Map<String, ClassFileResourceProvider> descs = Maps.newHashMap();
                                for (ClassFileResourceProvider provider : providers) {
                                    for (String s : provider.getClassDescriptors()) {
                                        if (!descs.containsKey(s)) {
                                            descs.put(s, provider);
                                        }
                                    }
                                }
                                return descs;
                            });
        }

        @Override
        public Set<String> getClassDescriptors() {
            return descriptors.get().keySet();
        }

        @Override
        public ProgramResource getProgramResource(String descriptor) {
            ClassFileResourceProvider provider = descriptors.get().get(descriptor);
            if (provider == null) {
                return null;
            }
            return provider.getProgramResource(descriptor);
        }
    }

    @NonNull private static final AtomicLong nextId = new AtomicLong();

    @NonNull private List<ClassFileResourceProvider> providers;
    @NonNull private final OrderedClassFileResourceProvider orderedClassFileResourceProvider;
    private final long id;

    public ClassFileProviderFactory(@NonNull Collection<Path> paths) throws IOException {
        id = nextId.addAndGet(1);

        providers = Lists.newArrayListWithExpectedSize(paths.size());
        for (Path path : paths) {
            if (path.toFile().exists()) {
                providers.add(createProvider(path));
            }
        }

        orderedClassFileResourceProvider = new OrderedClassFileResourceProvider(providers);
    }

    public long getId() {
        return id;
    }

    @Override
    public void close() throws IOException {
        // Close providers and clear
        for (ClassFileResourceProvider provider : providers) {
            if (provider instanceof Closeable) {
                ((Closeable) provider).close();
            }
        }
        providers.clear();
    }

    @NonNull
    public ClassFileResourceProvider getOrderedProvider() {
        return orderedClassFileResourceProvider;
    }

    @NonNull
    private static ClassFileResourceProvider createProvider(@NonNull Path entry)
            throws IOException {
        if (Files.isRegularFile(entry)) {
            return new CachingArchiveClassFileProvider(entry);
        } else if (Files.isDirectory(entry)) {
            return DirectoryClassFileProvider.fromDirectory(entry);
        } else {
            throw new FileNotFoundException(entry.toString());
        }
    }
}
