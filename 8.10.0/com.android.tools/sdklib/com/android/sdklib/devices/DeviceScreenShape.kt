/*
 * Copyright (C) 2024 The Android Open Source Project
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
@file:JvmName("DeviceScreenShape")
package com.android.sdklib.devices

import com.android.resources.ScreenRound
import java.awt.Dimension
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D

/**
 * Returns the [Shape] of the [Device]'s [Screen].
 * @param originX the X coordinate of the origin of the resulting [Shape].
 * @param originY the Y coordinate of the origin of the resulting [Shape].
 * @param size the [Dimension] (width and height) the resulting [Shape] should fit in.
 */
fun Device.screenShape(originX: Double, originY: Double, size: Dimension): Shape? {
    val screen = this.defaultHardware.screen
    if (screen.screenRound != ScreenRound.ROUND) {
        return null
    }

    val chin = screen.chin
    if (chin == 0) {
        // Plain circle
        return Ellipse2D.Double(
            originX,
            originY,
            size.width.toDouble(),
            size.height.toDouble(),
        )
    } else {
        val height = size.height * chin / screen.yDimension
        val a1 =
            Area(
                Ellipse2D.Double(
                    originX,
                    originY,
                    size.width.toDouble(),
                    (size.height + height).toDouble(),
                )
            )
        val a2 =
            Area(
                Rectangle2D.Double(
                    originX,
                    (originY + 2 * (size.height + height) - height),
                    size.width.toDouble(),
                    height.toDouble(),
                )
            )
        a1.subtract(a2)
        return a1
    }
}
