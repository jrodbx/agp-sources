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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.dx.cf.code.SimException;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ByteArrayAnnotatedOutput;
import com.android.ide.common.blame.parser.DexParser;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

class DxDexArchiveBuilder extends DexArchiveBuilder {

    private static final Logger LOGGER = Logger.getLogger(DxDexArchiveBuilder.class.getName());

    private final DexArchiveBuilderConfig config;
    @Nullable private byte[] inStorage;
    @Nullable private DexFile.Storage outStorage;

    public DxDexArchiveBuilder(DexArchiveBuilderConfig config) {
        this.config = config;
    }

    @Override
    public void convert(
            @NonNull Stream<ClassFileEntry> input,
            @NonNull Path output,
            @Nullable DependencyGraphUpdater<File> desugarGraphUpdater)
            throws DexArchiveBuilderException {
        Preconditions.checkArgument(desugarGraphUpdater == null);

        Iterator<ClassFileEntry> iterator = input.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        outStorage =
                config.getOutBufferSize() > 0
                        ? new DexFile.Storage(new byte[config.getOutBufferSize()])
                        : null;
        inStorage = config.getInBufferSize() > 0 ? new byte[config.getInBufferSize()] : null;
        ClassFileEntry classFileEntry = null;
        try (DexArchive outputDexArchive = DexArchives.fromInput(output)) {
            while (iterator.hasNext()) {
                classFileEntry = iterator.next();
                ByteArray byteArray;
                if (inStorage == null) {
                    byteArray = new ByteArray(classFileEntry.readAllBytes());
                } else {
                    if (classFileEntry.getSize() > inStorage.length) {
                        if (LOGGER.isLoggable(Level.FINER)) {
                            LOGGER.log(
                                    Level.FINER,
                                    "File too big "
                                            + classFileEntry.getSize()
                                            + " : "
                                            + classFileEntry.getRelativePath()
                                            + " vs "
                                            + inStorage.length);
                        }
                        inStorage = new byte[(int) classFileEntry.getSize()];
                    }
                    int readBytes = classFileEntry.readAllBytes(inStorage);
                    byteArray = new ByteArray(inStorage, 0, readBytes);
                }
                dex(classFileEntry.getRelativePath(), byteArray, outputDexArchive);
            }
        } catch (RuntimeException | IOException e) {
            throw getExceptionToRethrow(e, classFileEntry);
        }
    }

    public void dex(String relativePath, ByteArray classBytes, DexArchive output)
            throws IOException {

        // Copied from dx, from com.android.dx.command.dexer.Main
        DirectClassFile cf = new DirectClassFile(classBytes, relativePath, true);
        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        cf.getMagic(); // triggers the actual parsing

        // starts the actual translation and writes the content to the dex file
        // specified
        DexFile dexFile = new DexFile(config.getDexOptions());

        // Copied from dx, from com.android.dx.command.dexer.Main
        ClassDefItem classDefItem =
                CfTranslator.translate(
                        config.getDxContext(),
                        cf,
                        null,
                        config.getCfOptions(),
                        config.getDexOptions(),
                        dexFile);
        dexFile.add(classDefItem);

        if (outStorage != null) {
            ByteArrayAnnotatedOutput byteArrayAnnotatedOutput = dexFile.writeTo(outStorage);
            output.addFile(
                    ClassFileEntry.withDexExtension(relativePath),
                    byteArrayAnnotatedOutput.getArray(),
                    0,
                    byteArrayAnnotatedOutput.getCursor());
        } else {
            byte[] bytes = dexFile.toDex(null, false);
            output.addFile(ClassFileEntry.withDexExtension(relativePath), bytes, 0, bytes.length);
        }
    }

    @NonNull
    private DexArchiveBuilderException getExceptionToRethrow(
            @NonNull Throwable t, @Nullable ClassFileEntry entry) {
        StringBuilder msg = new StringBuilder();
        msg.append("Error while dexing ");
        if (entry != null) {
            msg.append(entry.getRelativePath());
        }
        msg.append(System.lineSeparator());
        if (t instanceof SimException
                && t.getMessage().startsWith(DexParser.ERROR_INVOKE_DYNAMIC)) {
            msg.append(DexParser.getEnableDesugaringHint(26));
        }

        return new DexArchiveBuilderException(msg.toString(), t);
    }
}
