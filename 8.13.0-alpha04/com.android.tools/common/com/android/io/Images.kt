/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("Images")
package com.android.io

import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOException
import javax.imageio.ImageIO

/**
 * Reads an image from a file.
 *
 * Similar to [ImageIO.read], but throws an [IOException] if the format of the input file was not
 * recognized and a [java.nio.file.NoSuchFileException] if the input file is not found.
 *
 * @return a [BufferedImage] containing the decoded contents of the image file
 *
 * @exception java.nio.file.NoSuchFileException if the input file is not found
 * @exception IOException if an error occurs during reading or if the format of the input file was
 *     not recognized
 */
@Throws(IOException::class)
fun Path.readImage(): BufferedImage {
    Files.newInputStream(this).use { stream ->
        return ImageIO.read(stream) ?: throw IIOException("Unrecognized image format in file $this")
    }
}

/**
 * Writes the image to a file.
 *
 * Similar to [ImageIO.write], but throws an [IOException] if [formatName] is not recognized.
 *
 * Writes an image using an arbitrary `ImageWriter` that supports the given format to
 * a file. If there is already a file present, its contents are overwritten.
 *
 * @param formatName the informal name of the format, e.g. "PNG", "JPEG" or "WEBP"
 * @param output the file to write the image to
 * @exception IOException if an error occurs during writing
 */
@Throws(IOException::class)
fun RenderedImage.writeImage(formatName: String, output: Path) {
    Files.newOutputStream(output).use { stream ->
        if (!ImageIO.write(this, formatName, stream)) {
            throw IIOException("Unrecognized image format \"$formatName\"")
        }
    }
}
