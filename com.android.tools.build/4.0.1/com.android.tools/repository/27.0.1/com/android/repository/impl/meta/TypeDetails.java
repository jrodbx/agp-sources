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

package com.android.repository.impl.meta;

import com.android.repository.api.RepoPackage;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Abstract superclass for xjc-created JAXB-usable types.
 * <p>
 * Each {@link RepoPackage} can optionally contain an instance of a subclass of {@code TypeDetails}.
 * Each JAXB-usable subclass should itself implement an interface that can be used to access the
 * actual type information and methods of the concrete subclass.
 * <p>
 * Notably this class is used to create {@link JAXBElement}s when marshalling, since it is the one
 * class that has access to the {@code ObjectFactory} of the relevant extension (namely, the
 * extension in which it was defined).
 */
@XmlTransient
public abstract class TypeDetails {

    @XmlTransient
    public interface GenericType {}
}
