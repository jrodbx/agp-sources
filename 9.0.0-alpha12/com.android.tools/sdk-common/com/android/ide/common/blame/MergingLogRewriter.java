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

package com.android.ide.common.blame;


import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A proxy {@link MessageReceiver} that uses a {@link MergingLog} to rewrite {@link Message}s to
 * point to their original files.
 */
public class MergingLogRewriter implements MessageReceiver {

    @NonNull
    private final MessageReceiver mMessageReceiver;

    @NonNull
    private final Function<SourceFilePosition, SourceFilePosition> mGetOriginalPosition;

    /**
     * Creates a new MessageLogRewriter.
     *
     * @param mergingLogLookup a function to look up the original resource position
     * @param messageReceiver the MessageReceiver to notify with the rewritten messages.
     */
    public MergingLogRewriter(
            @NonNull Function<SourceFilePosition, SourceFilePosition> mergingLogLookup,
            @NonNull MessageReceiver messageReceiver) {
        mMessageReceiver = messageReceiver;
        mGetOriginalPosition = mergingLogLookup;
    }

    @Override
    public void receiveMessage(@NonNull Message message) {
        List<SourceFilePosition> originalPositions = message.getSourceFilePositions();

        Iterable<SourceFilePosition> positions =
                originalPositions.stream().map(mGetOriginalPosition).collect(Collectors.toList());

        mMessageReceiver.receiveMessage(
                new Message(
                        message.getKind(),
                        message.getText(),
                        message.getRawMessage(),
                        message.getToolName(),
                        ImmutableList.copyOf(positions)));
    }

    public static Function<SourceFilePosition, SourceFilePosition> rewriteDir(File from, File to) {
        return input -> {
            File file = input.getFile().getSourceFile();
            if (file != null && file.toPath().startsWith(from.toPath())) {
                return new SourceFilePosition(
                        new SourceFile(
                                to.toPath()
                                        .resolve(from.toPath().relativize(file.toPath()))
                                        .toFile()),
                        input.getPosition());
            }
            return input;
        };
    }
}
