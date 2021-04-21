/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException

/**
 * At the user's request, attempt to symlink NDK to the given folder.
 *
 * The purpose of this is to allow the user to map their NDK to a shorter path location
 * on Windows so they have a better chance of building within the limits of Windows
 * path size constraint.
 *
 * In local.properties, user can set a value ndk.symlinkdir. For example,
 *
 *   ndk.symlinkdir = C/:/\ndk
 *
 * The weird slashing is the way local.properties requires special characters to be
 * escaped.
 *
 * Given the setting above, this function will try to create a symlink from the NDK
 * location to a folder based on the folder given.
 *
 * The version of the NDK is added to the path, so the actual location will be
 * something like:
 *
 *   C:\ndk\17.2.4988734
 *
 * The purpose of this version is to avoid remapping the same folder to different
 * versions of the NDK which could lead to difficult to diagnose errors.
 *
 * In all cases, a path is returned from this function. It is either the new symlink
 * folder or the original NDK folder. If the function couldn't do the symlink it
 * for some reason then it will issue an error and will return the original NDK
 * location so that configuration can continue.
 *
 * @param originalNdkFolder The original NDK file location before symlinking
 *
 * @param cxxVariantFolder The build folder without ABI subfolder. For example,
 *   .cxx/cmake/debug.  If the ndkSymlinkFolder is a relative path, then it is
 *   resolved relative to this folder.
 *
 * @param ndkSymlinkFolder The folder that the user has requested in local.settings.
 *   If it is null then the user didn't request a symlink and the original NDK location
 *   is returned. Otherwise, it is a relative or absolute folder name to symlink to.
 */
fun trySymlinkNdk(
    originalNdkFolder : File,
    cxxVariantFolder : File,
    ndkSymlinkFolder : File?) : File {
    if (ndkSymlinkFolder == null) {
        return originalNdkFolder
    }
    if (ndkSymlinkFolder.path.contains("$")) {
        warnln("Could not symlink from $originalNdkFolder to request " +
                "$ndkSymlinkFolder because that path contains '$'")
        return originalNdkFolder
    }
    if (!originalNdkFolder.isDirectory) {
        warnln("Could not symlink from $originalNdkFolder to request " +
                "$ndkSymlinkFolder because $originalNdkFolder doesn't exist")
        return originalNdkFolder
    }

    // Attempt to get source.properties from the NDK folder. This is partially to validate the
    // NDK folder but also to get the NDK version to use as a sub-folder.
    if (!originalNdkFolder.toPath().resolve("source.properties").toFile().isFile) {
        warnln("Could not symlink from $originalNdkFolder to request " +
                "$ndkSymlinkFolder because $originalNdkFolder doesn't have " +
                "source.properties")
        return originalNdkFolder
    }

    val version = SdkSourceProperties.tryReadPackageRevision(originalNdkFolder)

    // If it doesn't look like an NDK then abort
    if (version == null) {
        warnln("Could not symlink from $originalNdkFolder to request " +
                "$ndkSymlinkFolder because $originalNdkFolder doesn't have " +
                "source.properties that looks like NDK")
        return originalNdkFolder
    }

    val absoluteNdkSymlinkFolder =
        cxxVariantFolder.toPath().resolve(ndkSymlinkFolder.path).resolve("ndk").normalize()

    // Create the parent folder of the symlink requested.
    absoluteNdkSymlinkFolder.toFile().mkdirs()

    val versionedSymlinkFolder = absoluteNdkSymlinkFolder.resolve(version)

    // If the target folder already exists then return it. This assumes it was symlinked.
    if (versionedSymlinkFolder.toFile().exists()) {
        infoln("Symlink target $versionedSymlinkFolder already existed")
        return versionedSymlinkFolder.toFile()
    }

    // Follow any already-existing symlink to get the underlying real path
    val originalNdkFolderRealPath = originalNdkFolder.toPath().toRealPath()

    infoln("Symlinking NDK folder $originalNdkFolder to $versionedSymlinkFolder")

    return try {
        Files.createSymbolicLink(
            versionedSymlinkFolder,
            originalNdkFolderRealPath
        ).toFile()
    } catch (e: FileAlreadyExistsException) {
        // The symlinked folder already existed. This isn't a problem. Just return the symlink path
        infoln("Symlink target $versionedSymlinkFolder already existed")
        versionedSymlinkFolder.toFile()
    } catch (e: IOException) {
        // Couldn't create a link so use the original folder
        warnln("Could not symlink NDK folder $originalNdkFolder to " +
                "$versionedSymlinkFolder due to exception $e")
        originalNdkFolder
    }
}