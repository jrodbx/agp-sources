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

package com.android.build.gradle.internal.pipeline;

import static com.android.SdkConstants.DOT_JAR;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.InputFileDetails;

/**
 * Helper to handle the folder structure in the output of transforms.
 */
public class IntermediateFolderUtils {

    @NonNull private final File rootFolder;
    private final Set<ContentType> types;
    private final Set<? super Scope> scopes;
    // current list of outputs.
    private List<SubStream> subStreams;
    // list of outputs that were found in the list, but that are already marked as removed.
    private List<SubStream> removedSubStreams;
    private List<SubStream> outOfScopeStreams;
    private int nextIndex = 0;

    public IntermediateFolderUtils(
            @NonNull File rootFolder,
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes,
            boolean ignoreUnexpectedScopes) {
        this.rootFolder = rootFolder;
        this.types = types;
        this.scopes = scopes;
        updateLists(
                makeRestrictedCopies(SubStream.loadSubStreams(rootFolder), ignoreUnexpectedScopes));
    }

    @NonNull
    public File getRootFolder() {
        return rootFolder;
    }

    /**
     * Returns the location of content for a given set of name, Scopes, Content Types, and format.
     *
     * <p>If the format is {@link Format#DIRECTORY} then the result is the file location of the
     * folder. If the format is {@link Format#JAR} then the result is a file representing the jar to
     * create.
     *
     * @param name a unique name for the content. For a given set of scopes/types/format it must be
     *     unique.
     * @param types the content types associated with this content.
     * @param scopes the scopes associated with this content.
     * @param format the format of the content.
     * @return the location of the content.
     */
    @NonNull
    public synchronized File getContentLocation(
            @NonNull String name,
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes,
            @NonNull Format format) {
        // runtime check these since it's (indirectly) called by 3rd party transforms.
        checkNotNull(name);
        checkNotNull(types);
        checkNotNull(scopes);
        checkNotNull(format);
        checkState(!name.isEmpty());
        checkState(!types.isEmpty());
        checkState(!scopes.isEmpty());

        // search for an existing matching substream.
        for (SubStream subStream : subStreams) {
            // look for an existing match. This means same name, types, scopes, and format.
            if (name.equals(subStream.getName())
                    && types.equals(subStream.getTypes())
                    && scopes.equals(subStream.getScopes())
                    && format == subStream.getFormat()) {
                return new File(rootFolder, subStream.getFilename());
            }
        }

        // didn't find a matching output. create the new output
        SubStream newSubStream = new SubStream(name, nextIndex++, scopes, types, format, true);

        subStreams.add(newSubStream);

        return new File(rootFolder, newSubStream.getFilename());
    }

    @NonNull
    public TransformInput computeNonIncrementalInputFromFolder() {
        final List<JarInput> jarInputs = Lists.newArrayList();
        final List<DirectoryInput> directoryInputs = Lists.newArrayList();

        for (SubStream subStream : subStreams) {
            if (subStream.getFormat() == Format.DIRECTORY) {
                directoryInputs.add(
                        new ImmutableDirectoryInput(
                                subStream.getName(),
                                new File(rootFolder, subStream.getFilename()),
                                subStream.getTypes(),
                                subStream.getScopes()));

            } else {
                jarInputs.add(
                        new ImmutableJarInput(
                                subStream.getName(),
                                new File(rootFolder, subStream.getFilename()),
                                Status.NOTCHANGED,
                                subStream.getTypes(),
                                subStream.getScopes()));
            }
        }

        return new ImmutableTransformInput(jarInputs, directoryInputs, rootFolder);
    }

    @NonNull
    public Collection<File> getFiles(@NonNull StreamFilter streamFilter) {
        List<File> files = Lists.newArrayListWithExpectedSize(subStreams.size());
        for (SubStream stream : subStreams) {
            if (streamFilter.accept(stream.getTypes(), stream.getScopes())) {
                files.add(new File(rootFolder, stream.getFilename()));
            }
        }

        return files;
    }

    public void reload() {
        updateLists(makeRestrictedCopies(SubStream.loadSubStreams(rootFolder), false));
    }

    class IntermediateTransformInput extends IncrementalTransformInput {

        @NonNull
        private final File inputRoot;
        private List<String> rootLocationSegments = null;

        IntermediateTransformInput(@NonNull File inputRoot) {
            this.inputRoot = inputRoot;
        }

        @Override
        protected boolean checkRemovedFolder(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            if (!checkRootSegments(fileSegments)) {
                return false;
            }

            // there must be at least 2 additional segments (1 to the root of the folder and 1 for
            // the file inside.
            if (fileSegments.size() < rootLocationSegments.size() + 2) {
                return false;
            }

            // now check that the segments after the root are what we expect.
            int index = rootLocationSegments.size();
            String foldername = fileSegments.get(index);

            // First loop on sub-streams we care about and on match, create a new Input
            for (SubStream subStream : subStreams) {
                if (subStream.getFilename().equals(foldername)
                        && subStream.getFormat() == Format.DIRECTORY) {

                    // create the mutable folder for it?
                    MutableDirectoryInput folder =
                            new MutableDirectoryInput(
                                    subStream.getName(),
                                    new File(rootFolder, foldername),
                                    subStream.getTypes(),
                                    subStream.getScopes());
                    // add this file to it.
                    Logging.getLogger(TransformManager.class)
                            .info("Tagged" + file.getAbsolutePath() + " as removed");
                    folder.addChangedFile(file, Status.REMOVED);

                    // add it to the list.
                    addFolderInput(folder);

                    return true;
                }
            }

            // now loop on removed sub-streams. These can contain matching and non matching-streams
            // so we may create an input or not.
            for (SubStream subStream : removedSubStreams) {
                if (subStream.getFilename().equals(foldername)
                        && subStream.getFormat() == Format.DIRECTORY) {
                    // we need to check if the type/scope of this file matches this stream,
                    // as we could be using a sub-stream.
                    if (!Sets.intersection(transformInputTypes, subStream.getTypes()).isEmpty()
                            && !Sets.intersection(transformScopes, subStream.getScopes())
                                    .isEmpty()) {
                        // create the mutable folder for it?
                        MutableDirectoryInput folder =
                                new MutableDirectoryInput(
                                        subStream.getName(),
                                        new File(rootFolder, foldername),
                                        subStream.getTypes(),
                                        subStream.getScopes());
                        // add this file to it.
                        Logging.getLogger(TransformManager.class)
                                .info("Tagged" + file.getAbsolutePath() + " as removed");
                        folder.addChangedFile(file, Status.REMOVED);

                        // add it to the list.
                        addFolderInput(folder);
                    }

                    // return true whether the sub-stream is a scope/type match, to mention
                    // we know about the file.
                    return true;
                }
            }

            // then loop on the out of scope/type sub-streams and just acknowledge the file
            // is part of the stream if it's a name match.
            for (SubStream subStream : outOfScopeStreams) {
                if (subStream.getFilename().equals(foldername)
                        && subStream.getFormat() == Format.DIRECTORY) {
                    return true;
                }
            }

            return false;
        }

        @Override
        boolean checkRemovedJarFile(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            if (!checkRootSegments(fileSegments)) {
                return false;
            }

            // there must be only 1 additional segments.
            if (fileSegments.size() != rootLocationSegments.size() + 1) {
                return false;
            }

            String filename = file.getName();

            // last segment must end in .jar
            if (!filename.endsWith(DOT_JAR)) {
                return false;
            }

            // First loop on sub-streams we care about and on match, create a new Input
            for (SubStream subStream : subStreams) {
                if (subStream.getFilename().equals(filename)
                        && subStream.getFormat() == Format.JAR) {
                    // create the jar input
                    addImmutableJar(
                            new ImmutableJarInput(
                                    subStream.getName(),
                                    file,
                                    Status.REMOVED,
                                    subStream.getTypes(),
                                    subStream.getScopes()));
                    return true;
                }
            }

            // now loop on removed sub-streams. These can contain matching and non matching-streams
            // so we may create an input or not.
            for (SubStream subStream : removedSubStreams) {
                if (subStream.getFilename().equals(filename)
                        && subStream.getFormat() == Format.JAR) {
                    // we need to check if the type/scope of this file matches this stream,
                    // as we could be using a sub-stream.
                    if (!Sets.intersection(transformInputTypes, subStream.getTypes()).isEmpty()
                            && !Sets.intersection(transformScopes, subStream.getScopes())
                                    .isEmpty()) {
                        addImmutableJar(
                                new ImmutableJarInput(
                                        subStream.getName(),
                                        file,
                                        Status.REMOVED,
                                        subStream.getTypes(),
                                        subStream.getScopes()));
                    }

                    // return true whether the sub-stream is a scope/type match, to mention
                    // we know about the file.
                    return true;
                }
            }

            // then loop on the out of scope/type sub-streams and just acknowledge the file
            // is part of the stream if it's a name match.
            for (SubStream subStream : outOfScopeStreams) {
                if (subStream.getFilename().equals(filename)
                        && subStream.getFormat() == Format.JAR) {
                    return true;
                }
            }

            return false;
        }

        private boolean checkRootSegments(@NonNull List<String> fileSegments) {
            if (rootLocationSegments == null) {
                rootLocationSegments = Lists.newArrayList(
                        Splitter.on(File.separatorChar).split(inputRoot.getAbsolutePath()));
            }

            if (fileSegments.size() <= rootLocationSegments.size()) {
                return false;
            }

            // compare segments going backward as the leafs are more likely to be different.
            // We can ignore the segments of the file that are beyond the segments for the folder.
            for (int i = rootLocationSegments.size() - 1 ; i >= 0 ; i--) {
                if (!rootLocationSegments.get(i).equals(fileSegments.get(i))) {
                    return false;
                }
            }

            return true;
        }

    }

    @NonNull
    public IncrementalTransformInput computeIncrementalInputFromFolder() {
        final IncrementalTransformInput input = new IntermediateTransformInput(rootFolder);

        for (SubStream subStream : subStreams) {
            if (subStream.getFormat() == Format.DIRECTORY) {
                input.addFolderInput(
                        new MutableDirectoryInput(
                                subStream.getName(),
                                new File(rootFolder, subStream.getFilename()),
                                subStream.getTypes(),
                                subStream.getScopes()));

            } else {
                input.addJarInput(
                        new QualifiedContentImpl(
                                subStream.getName(),
                                new File(rootFolder, subStream.getFilename()),
                                subStream.getTypes(),
                                subStream.getScopes()));
            }
        }

        return input;
    }

    @NonNull
    static Status inputFileDetailsToStatus(@NonNull InputFileDetails inputFileDetails) {
        if (inputFileDetails.isAdded()) return Status.ADDED;
        if (inputFileDetails.isModified()) return Status.CHANGED;
        if (inputFileDetails.isRemoved()) return Status.REMOVED;
        return Status.NOTCHANGED;
    }

    public void save() throws IOException {
        // create a copy list with a new present flag based on whether the output actually
        // exists.
        // Don't process the removedSubStreams as they were removed in a previous run.
        List<SubStream> copyList = Lists.newArrayListWithCapacity(subStreams.size());
        for (SubStream subStream : subStreams) {
            copyList.add(
                    subStream.duplicateWithPresent(
                            new File(rootFolder, subStream.getFilename()).exists()));
        }

        // save that list.
        SubStream.save(copyList, rootFolder);

        // and use to to re-fill the normal and removed list for the next transform in case
        // it's happening in the same graph execution.
        updateLists(copyList);
    }

    @NonNull
    private Collection<SubStream> makeRestrictedCopies(
            @NonNull Collection<SubStream> streams, boolean ignoreUnexpectedScopes) {
        List<SubStream> list = Lists.newArrayListWithCapacity(streams.size());
        outOfScopeStreams = Lists.newArrayList();

        for (SubStream subStream : streams) {
            if (subStream.getIndex() >= nextIndex) {
                nextIndex = subStream.getIndex() + 1;
            }

            if (subStream.isPresent()) {
                // check these are types we care about and only pass down types we care about.
                // In this case we can safely return the content with a limited type,
                // as file extension allows for differentiation.
                Set<ContentType> limitedTypes = Sets.intersection(types, subStream.getTypes());
                if (!limitedTypes.isEmpty()) {
                    // We consider compatible sub-stream to:
                    // - contains 1+ of the main stream scopes
                    // - and not contain any other scopes.
                    // SubStream that only contains unwanted scopes are fine
                    // SubStream that contains some required scope and some other scope generate
                    // an exception.
                    boolean foundUnwanted = false;
                    boolean foundMatch = false;
                    for (Object scope : subStream.getScopes()) {
                        //noinspection SuspiciousMethodCalls
                        if (scopes.contains(scope)) {
                            foundMatch = true;
                        } else {
                            foundUnwanted = true;
                        }
                    }

                    if (foundMatch && foundUnwanted && !ignoreUnexpectedScopes) {
                        // Sort the names, so the error message is stable.
                        List<String> foundScopes =
                                subStream
                                        .getScopes()
                                        .stream()
                                        .map(Object::toString)
                                        .sorted()
                                        .collect(Collectors.toList());

                        throw new RuntimeException(
                                String.format(
                                        "Unexpected scopes found in folder '%s'. Required: %s. Found: %s",
                                        rootFolder,
                                        Joiner.on(", ").join(scopes),
                                        Joiner.on(", ").join(foundScopes)));
                    } else if (foundMatch) {
                        // if the types are an exact match, then just get the substream
                        if (limitedTypes.size() == subStream.getTypes().size()) {
                            list.add(subStream);
                        } else {
                            // make a restricted copy.
                            list.add(
                                    new SubStream(
                                            subStream.getName(),
                                            subStream.getIndex(),
                                            subStream.getScopes(),
                                            limitedTypes,
                                            subStream.getFormat(),
                                            subStream.isPresent()));

                            // and keep the rest around, as out of scope to detect (and ignore)
                            // changed files. Because we only take streams with a subset only of
                            // required scopes, we know there's no need to get the
                            outOfScopeStreams.add(
                                    new SubStream(
                                            subStream.getName(),
                                            subStream.getIndex(),
                                            subStream.getScopes(),
                                            minus(subStream.getTypes(), limitedTypes),
                                            subStream.getFormat(),
                                            subStream.isPresent()));
                        }

                    } else {
                        // no match at all, add to output of scope streams.
                        outOfScopeStreams.add(subStream);
                    }
                } else {
                    // no match at all, add to output of scope streams.
                    outOfScopeStreams.add(subStream);
                }
            } else {
                // don't do filtering for removed streams.
                list.add(subStream);
            }
        }

        return list;
    }

    private static <T> Set<T> minus(Set<T> main, Set<T> minus) {
        Set<T> result = Sets.newHashSet(main);
        result.removeAll(minus);
        return result;
    }

    private void updateLists(@NonNull Collection<SubStream> subStreamList) {
        subStreams =
                subStreamList.stream().filter(SubStream::isPresent).collect(Collectors.toList());
        removedSubStreams =
                subStreamList
                        .stream()
                        .filter(subStream -> !subStream.isPresent())
                        .collect(Collectors.toList());
    }
}
