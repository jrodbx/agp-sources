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
package com.android.sdklib;

import com.android.io.CancellableFileIo;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** An {@link IAbstractFile} or {@link IAbstractFolder} that wraps a {@link Path}. */
public class PathFileWrapper implements IAbstractFile, IAbstractFolder {

    private final Path mFile;

    public PathFileWrapper(Path path) {
        mFile = path;
    }

    @Override
    public InputStream getContents() throws StreamException {
        try {
            return CancellableFileIo.newInputStream(mFile);
        } catch (IOException e) {
            throw new StreamException(e, this);
        }
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
        try (OutputStream fos = Files.newOutputStream(mFile)) {

            byte[] buffer = new byte[1024];
            int count;
            while ((count = source.read(buffer)) != -1) {
                fos.write(buffer, 0, count);
            }
        } catch (IOException e) {
            throw new StreamException(e, this);
        }
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
        try {
            return Files.newOutputStream(mFile);
        } catch (IOException ex) {
            throw new StreamException(ex, this);
        }
    }

    @Override
    public String getOsLocation() {
        return mFile.toAbsolutePath().toString();
    }

    @Override
    public boolean exists() {
        return CancellableFileIo.exists(mFile);
    }

    @Override
    public IAbstractFile getFile(String name) {
        return new PathFileWrapper(mFile.resolve(name));
    }
}
