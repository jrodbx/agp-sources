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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.google.common.collect.Maps;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a versioned set of generated classes corresponding to a versioned XML schema.
 *
 * This can be a single stand-alone schema, or an extension to an existing schema. For example,
 * {@code repo-common-N.xsd} defines a schema for a repository with packages, and the collection of
 * the schemas for all N would be represented by a single {@code SchemaModule} instance.
 * They can then be used for marshalling or unmarshalling XML documents by the repository framework.
 */
@XmlTransient
public class SchemaModule<T> {

    /**
     * Map of XML namespaces to the SchemaModuleVersions making up this module.
     */
    private final Map<String, SchemaModuleVersion> mVersions = Maps.newHashMap();

    /**
     * Reference to the highest version found. Used by default when creating new objects.
     */
    private final SchemaModuleVersion<T> mLatestVersion;

    /**
     * Class used with {@link Class#getResourceAsStream(String)} to look up xsd resources.
     */
    private final Class mResourceRoot;

    /**
     * @param ofPattern Fully-qualified class name of the JAXB {@code ObjectFactory} classes
     *                  making up this module. Should have a single %d parameter, corresponding to
     *                  the 1-indexed version of the schema.
     * @param xsdPattern Filename pattern of the XSDs making up this module. Should have a single
     *                   %d parameter, corresponding to the 1-indexed version of the schema.
     * @param resourceRoot A class instance used via {@link Class#getResource(String)} to read
     *                     the XSD file.
     */
    public SchemaModule(@NonNull String ofPattern, @NonNull String xsdPattern, @NonNull Class resourceRoot) {
        if (!ofPattern.matches(".*%[0-9.$]*d.*") || !xsdPattern.matches(".*%[0-9.$]*d.*")) {
            assert false : "ofPattern and xsdPattern must contain a single %d parameter";
        }
        SchemaModuleVersion<T> version = null;
        for (int i = 1; ; i++) {
            Class<? extends T> objectFactory;
            try {
                objectFactory =
                        (Class<? extends T>) Class.forName(String.format(Locale.US, ofPattern, i));
            } catch (ClassNotFoundException e) {
                break;
            }
            String xsdLocation = String.format(Locale.US, xsdPattern, i);
            version = new SchemaModuleVersion<>(objectFactory, xsdLocation);
            mVersions.put(version.getNamespace(), version);
        }
        mLatestVersion = version;
        assert !mVersions.isEmpty() : "No versions found";
        mResourceRoot = resourceRoot;
    }

    /**
     * Creates an {@code ObjectFactory} for the latest known version of this module.
     */
    @NonNull
    public T createLatestFactory() {
        Class<? extends T> of = mLatestVersion.getObjectFactory();
        try {
            return of.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            assert false : e;
        }
        return null;
    }

    /**
     * Gets the map of namespaces to {@link SchemaModuleVersion}s. Should only be needed by
     * the repository framework.
     */
    @NonNull
    public Map<String, SchemaModuleVersion> getNamespaceVersionMap() {
        return mVersions;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SchemaModule)) {
            return false;
        }
        return mVersions.equals(((SchemaModule)obj).getNamespaceVersionMap());
    }

    @Override
    public int hashCode() {
        return mVersions.hashCode();
    }

    /**
     * Gets the namespace prefix (the namespace with the final number (if any) removed) for our
     * latest schema version.
     */
    @NonNull
    public String getNamespacePrefix() {
        return mLatestVersion.getNamespacePrefix();
    }

    /**
     * Gets the namespace of the our latest schema version.
     */
    public String getLatestNamespace() {
        return mLatestVersion.getNamespace();
    }

    /**
     * Represents a single version of a schema, including a single XSD and a single
     * {@code ObjectFactory}.
     */
    public class SchemaModuleVersion<R> {

        private final Class<? extends R> mObjectFactory;
        private final String mXsdLocation;
        private final String mNamespace;

        /**
         * @param objectFactory The xjc-generated {@code ObjectFactory} instance for this schema
         *                      version. Notably, the package containing this class must contain
         *                      a {@code package-info.java} with a {@link XmlSchema} annotation
         *                      giving the XML namespace of this schema.
         * @param xsdLocation The XSD file for this schema.
         */
        public SchemaModuleVersion(@NonNull Class<? extends R> objectFactory,
          @NonNull String xsdLocation) {
            mObjectFactory = objectFactory;
            mXsdLocation = xsdLocation;
            String namespace = objectFactory.getPackage().getAnnotation(XmlSchema.class)
                    .namespace();

            assert namespace != null : "Can't create schema module version with no namespace";
            mNamespace = namespace;
        }

        /**
         * Gets the {@code ObjectFactory} for this schema version.
         */
        @NonNull
        public Class<? extends R> getObjectFactory() {
            return mObjectFactory;
        }

        /**
         * Gets the XSD file for this schema version.
         */
        @NonNull
        public InputStream getXsd() {
            return mResourceRoot.getResourceAsStream(mXsdLocation);
        }

        /**
         * Gets the target namespace of this schema version.
         */
        @NonNull
        public String getNamespace() {
            return mNamespace;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SchemaModuleVersion)) {
                return false;
            }
            return mXsdLocation.equals(((SchemaModuleVersion)obj).mXsdLocation) &&
                   mNamespace.equals(((SchemaModuleVersion)obj).mNamespace);
        }

        @Override
        public int hashCode() {
            return mXsdLocation.hashCode() * 37 + mNamespace.hashCode();
        }

        /**
         * Gets our namespace prefix (the namespace with the final number (if any) removed).
         */
        @NonNull
        public String getNamespacePrefix() {
            return mNamespace.replaceAll("/[0-9]*$", "/");
        }
    }
}
