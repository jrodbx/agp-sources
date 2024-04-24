/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.shrinker;

public class DummyContent {

    // A 1x1 pixel PNG of type BufferedImage.TYPE_BYTE_GRAY
    public static final byte[] TINY_PNG =
            new byte[] {
                    (byte) -119, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10,
                    (byte) 26, (byte) 10, (byte) 0, (byte) 0, (byte) 0, (byte) 13,
                    (byte) 73, (byte) 72, (byte) 68, (byte) 82, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 1,
                    (byte) 8, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 58,
                    (byte) 126, (byte) -101, (byte) 85, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 10, (byte) 73, (byte) 68, (byte) 65, (byte) 84, (byte) 120,
                    (byte) -38, (byte) 99, (byte) 96, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 2, (byte) 0, (byte) 1, (byte) -27, (byte) 39, (byte) -34,
                    (byte) -4, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 73,
                    (byte) 69, (byte) 78, (byte) 68, (byte) -82, (byte) 66, (byte) 96,
                    (byte) -126
            };

    public static final long TINY_PNG_CRC = 0x88b2a3b0L;

    // A 3x3 pixel PNG of type BufferedImage.TYPE_INT_ARGB with 9-patch markers
    public static final byte[] TINY_9PNG =
            new byte[] {
                    (byte) -119, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10,
                    (byte) 26, (byte) 10, (byte) 0, (byte) 0, (byte) 0, (byte) 13,
                    (byte) 73, (byte) 72, (byte) 68, (byte) 82, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 3, (byte) 0, (byte) 0, (byte) 0, (byte) 3,
                    (byte) 8, (byte) 6, (byte) 0, (byte) 0, (byte) 0, (byte) 86,
                    (byte) 40, (byte) -75, (byte) -65, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 20, (byte) 73, (byte) 68, (byte) 65, (byte) 84, (byte) 120,
                    (byte) -38, (byte) 99, (byte) 96, (byte) -128, (byte) -128, (byte) -1,
                    (byte) 12, (byte) 48, (byte) 6, (byte) 8, (byte) -96, (byte) 8,
                    (byte) -128, (byte) 8, (byte) 0, (byte) -107, (byte) -111, (byte) 7,
                    (byte) -7, (byte) -64, (byte) -82, (byte) 8, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 73, (byte) 69, (byte) 78,
                    (byte) 68, (byte) -82, (byte) 66, (byte) 96, (byte) -126
            };

    public static final long TINY_9PNG_CRC = 0x1148f987L;

    // The XML document <x/> as binary-packed with AAPT
    public static final byte[] TINY_BINARY_XML =
            new byte[] {
                    (byte) 3, (byte) 0, (byte) 8, (byte) 0, (byte) 104, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 1, (byte) 0, (byte) 28, (byte) 0,
                    (byte) 36, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 1, (byte) 0, (byte) 0, (byte) 32, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 1,
                    (byte) 120, (byte) 0, (byte) 2, (byte) 1, (byte) 16, (byte) 0,
                    (byte) 36, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 0,
                    (byte) 0, (byte) 0, (byte) -1, (byte) -1, (byte) -1, (byte) -1,
                    (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 20, (byte) 0, (byte) 20, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 3, (byte) 1, (byte) 16, (byte) 0,
                    (byte) 24, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 0,
                    (byte) 0, (byte) 0, (byte) -1, (byte) -1, (byte) -1, (byte) -1,
                    (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0
            };

    public static final long TINY_BINARY_XML_CRC = 0xd7e65643L;

    // The XML document <x/> as a proto packed with AAPT2
    public static final byte[] TINY_PROTO_XML =
            new byte[] {0xa, 0x3, 0x1a, 0x1, 0x78, 0x1a, 0x2, 0x8, 0x1};
    public static final long TINY_PROTO_XML_CRC = 3204905971L;

    // The XML document <x/> as binary-packed with AAPT
    public static final byte[] TINY_PROTO_CONVERTED_TO_BINARY_XML =
            new byte[] {
                    (byte) 3, (byte) 0, (byte) 8, (byte) 0, (byte) 112, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 1, (byte) 0, (byte) 28, (byte) 0,
                    (byte) 36, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 1, (byte) 0, (byte) 0, (byte) 32, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 1,
                    (byte) 120, (byte) 0, (byte) -128, (byte) 1, (byte) 8, (byte) 0,
                    (byte) 8, (byte) 0, (byte) 0, (byte) 0, (byte) 2, (byte) 1,
                    (byte) 16, (byte) 0, (byte) 36, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) -1, (byte) -1,
                    (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) -1,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 20, (byte) 0,
                    (byte) 20, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 3, (byte) 1,
                    (byte) 16, (byte) 0, (byte) 24, (byte) 0, (byte) 0, (byte) 0,
                    (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) -1, (byte) -1,
                    (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) -1, (byte) -1,
                    (byte) 0, (byte) 0, (byte) 0, (byte) 0
            };

    private DummyContent() {}
}
