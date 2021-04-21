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

package com.android.builder.dexing;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.DesugarDiagnostic;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class D8DiagnosticsHandler implements DiagnosticsHandler {
    private final MessageReceiver messageReceiver;
    private final String toolTag;
    private final Set<String> pendingHints = new HashSet<>();

    public D8DiagnosticsHandler(MessageReceiver messageReceiver) {
        this(messageReceiver, "D8");
    }

    public D8DiagnosticsHandler(MessageReceiver messageReceiver, String toolTag) {
        this.messageReceiver = messageReceiver;
        this.toolTag = toolTag;
    }

    public static Origin getOrigin(ClassFileEntry entry) {
        Path root = entry.getInput().getPath();
        if (Files.isRegularFile(root)) {
            return new ArchiveEntryOrigin(entry.getRelativePath(), new PathOrigin(root));
        } else {
            return new PathOrigin(root.resolve(entry.getRelativePath()));
        }
    }

    public static Origin getOrigin(DexArchiveEntry entry) {
        Path root = entry.getDexArchive().getRootPath();
        if (Files.isRegularFile(root)) {
            return new ArchiveEntryOrigin(entry.getRelativePathInArchive(), new PathOrigin(root));
        } else {
            return new PathOrigin(root.resolve(entry.getRelativePathInArchive()));
        }
    }

    @Override
    public void error(Diagnostic warning) {
        messageReceiver.receiveMessage(convertToMessage(Message.Kind.ERROR, warning));
    }

    @Override
    public void warning(Diagnostic warning) {
        Message.Kind kind;
        if (warning instanceof DesugarDiagnostic) {
            kind = Message.Kind.INFO;
        } else {
            kind = Message.Kind.WARNING;
        }
        messageReceiver.receiveMessage(convertToMessage(kind, warning));
    }

    @Override
    public void info(Diagnostic info) {
        messageReceiver.receiveMessage(convertToMessage(Message.Kind.INFO, info));
    }

    public Set<String> getPendingHints() {
        return pendingHints;
    }

    protected void addHint(String hint) {
        synchronized (pendingHints) {
            pendingHints.add(hint);
        }
    }

    protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {
        String textMessage = diagnostic.getDiagnosticMessage();

        Origin origin = diagnostic.getOrigin();
        Position positionInOrigin = diagnostic.getPosition();
        SourceFilePosition position;
        if (origin instanceof PathOrigin) {
            File originFile = ((PathOrigin) origin).getPath().toFile();
            TextPosition startTextPosition;
            TextPosition endTextPosition;
            if (positionInOrigin instanceof TextRange) {
                TextRange textRange = (TextRange) positionInOrigin;
                startTextPosition = textRange.getStart();
                endTextPosition = textRange.getEnd();
            } else if (positionInOrigin instanceof TextPosition) {
                startTextPosition = (TextPosition) positionInOrigin;
                endTextPosition = startTextPosition;
            } else {
                startTextPosition = null;
                endTextPosition = null;
            }
            if (startTextPosition != null) {
                int startLine = startTextPosition.getLine();
                if (startLine != -1) {
                    startLine--;
                }
                int startColumn = startTextPosition.getColumn();
                if (startColumn != -1) {
                    startColumn--;
                }
                int endLine = endTextPosition.getLine();
                if (endLine != -1) {
                    endLine--;
                }
                int endColumn = endTextPosition.getColumn();
                if (endColumn != -1) {
                    endColumn--;
                }

                position =
                        new SourceFilePosition(
                                originFile,
                                new SourcePosition(
                                        startLine,
                                        startColumn,
                                        toIntOffset(startTextPosition.getOffset()),
                                        endLine,
                                        endColumn,
                                        toIntOffset(endTextPosition.getOffset())));

            } else {
                position = new SourceFilePosition(originFile, SourcePosition.UNKNOWN);
            }
        } else if (origin.parent() instanceof PathOrigin) {
            File originFile = ((PathOrigin) origin.parent()).getPath().toFile();
            position = new SourceFilePosition(originFile, SourcePosition.UNKNOWN);
        } else {
            position = SourceFilePosition.UNKNOWN;
            if (origin != Origin.unknown()) {
                textMessage = origin.toString() + ": " + textMessage;
            }
        }

        return new Message(kind, textMessage, textMessage, toolTag, position);
    }

    private static int toIntOffset(long offset) {
        if (offset >= 0 && offset <= Integer.MAX_VALUE) {
            return (int) offset;
        } else {
            return -1;
        }
    }
}
