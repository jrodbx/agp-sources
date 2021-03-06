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

package com.android.sdklib.repository.meta;

import com.android.annotations.NonNull;
import com.android.repository.impl.meta.TypeDetails;

/**
 * Parent class for {@code ObjectFactories} created by xjc from sdk-sys-img-XX.xsd, for
 * creating system image-schema-specific {@link TypeDetails}.
 */
public abstract class SysImgFactory {

    @NonNull
    public abstract DetailsTypes.SysImgDetailsType createSysImgDetailsType();
}
