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

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import com.android.repository.io.FileOp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An {@link IAbstractFile} or {@link IAbstractFolder} that wraps a {@link File} and uses a
 * {@link FileOp} for file operations, to allow mocking.
 */
public class FileOpFileWrapper implements IAbstractFile, IAbstractFolder {

    private final FileOp mFileOp;
    private final File mFile;
    private final boolean mIsFolder;

    public FileOpFileWrapper(File file, FileOp fop, boolean isFolder) {
        mFile = file;
        mFileOp = fop;
        mIsFolder = isFolder;
    }

    @Override
    public InputStream getContents() throws StreamException {
        try {
            return mFileOp.newFileInputStream(mFile);
        } catch (IOException e) {
            throw new StreamException(e, this);
        }
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
        OutputStream fos = null;
        try {
            fos = mFileOp.newFileOutputStream(mFile);

            byte[] buffer = new byte[1024];
            int count;
            while ((count = source.read(buffer)) != -1) {
                fos.write(buffer, 0, count);
            }
        } catch (IOException e) {
            throw new StreamException(e, this);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    throw new StreamException(e, this);
                }
            }
        }
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
        try {
            return mFileOp.newFileOutputStream(mFile);
        } catch (IOException ex) {
            throw new StreamException(ex, this);
        }
    }

    @Override
    public String getOsLocation() {
        return mFile.getAbsolutePath();
    }

    @Override
    public boolean exists() {
        return mIsFolder ? mFileOp.isDirectory(mFile) : mFileOp.isFile(mFile);
    }

    @Override
    public IAbstractFile getFile(String name) {
        return new FileOpFileWrapper(new File(mFile, name), mFileOp, false);
    }
}
