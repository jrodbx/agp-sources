/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.io;


import java.io.File;

/**
 * An implementation of {@link IAbstractFolder} extending {@link File}.
 */
public class FolderWrapper extends File implements IAbstractFolder {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new File instance by converting the given pathname string into an abstract
     * pathname.
     * @param pathname the pathname
     *
     * @see File#File(String)
     */
    public FolderWrapper(String pathname) {
        super(pathname);
    }

    @Override
    public IAbstractFile getFile(String name) {
        return new FileWrapper(this, name);
    }

    @Override
    public String getOsLocation() {
        return getAbsolutePath();
    }

    @Override
    public boolean exists() {
        return isDirectory();
    }
}
