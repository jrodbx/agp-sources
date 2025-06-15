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
package com.android.ddmlib.internal.jdwp;

import com.google.common.base.Objects;

/**
 * An id used to represent a unique tracked connection between ddmlib
 * {@link com.android.ddmlib.internal.ClientImpl} and ADB. Because multiple instances of ddmlib
 * can be running these Ids are used by the {@link JdwpClientManagerFactory} to ensure that all
 * {@link JdwpProxyClient}'s are manged by the same {@link JdwpClientManager}
 */
class JdwpClientManagerId {
  /**
   * The device serial number being tracked.
   */
  String deviceSerial;
  /**
   * The pid of the process being tracked.
   */
  int pid;

  JdwpClientManagerId(String deviceSerial, int pid) {
    this.deviceSerial = deviceSerial;
    this.pid = pid;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(deviceSerial, pid);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JdwpClientManagerId)) {
      return false;
    }
    JdwpClientManagerId other = (JdwpClientManagerId)obj;
    return deviceSerial.equals(other.deviceSerial) && pid == other.pid;
  }
}
