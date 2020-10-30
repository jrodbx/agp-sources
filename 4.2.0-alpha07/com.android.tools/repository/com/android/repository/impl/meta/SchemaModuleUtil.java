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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SchemaModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Utilities for working with {@link SchemaModule}s, including marshalling and unmarshalling with
 * JAXB.
 */
public class SchemaModuleUtil {

    private static final Map<String, JAXBContext> CONTEXT_CACHE = Maps.newHashMap();

    private static final Map<List<SchemaModule<?>.SchemaModuleVersion<?>>, Map<LSResourceResolver, Schema>>
            SCHEMA_CACHE = Maps.newHashMap();

    /**
     * Create an {@link LSResourceResolver} that will use the supplied {@link SchemaModule}s to
     * find an XSD from its namespace. This must be used when marshalling/unmarshalling if any
     * {@link SchemaModule}s contain XSDs which import others without specifying a complete
     * {@code schemaLocation}.
     */
    @Nullable
    public static LSResourceResolver createResourceResolver(
            @NonNull final Set<SchemaModule<?>> modules, @NonNull ProgressIndicator progress) {
        return new SchemaModuleResourceResolver(modules, progress);
    }

    /**
     * Creates a {@link JAXBContext} from the XSDs in the given {@link SchemaModule}s.
     */
    @NonNull
    private static JAXBContext getContext(@NonNull Collection<SchemaModule<?>> possibleModules) {
        List<String> packages = Lists.newArrayList();
        for (SchemaModule<?> module : possibleModules) {
            for (SchemaModule<?>.SchemaModuleVersion<?> version : module
                    .getNamespaceVersionMap().values()) {
                packages.add(version.getObjectFactory().getPackage().getName());
            }
        }
        String key = Joiner.on(":").join(packages);
        JAXBContext jc = CONTEXT_CACHE.get(key);
        if (jc == null) {
            try {
                jc = JAXBContext.newInstance(key, SchemaModuleUtil.class.getClassLoader());
                CONTEXT_CACHE.put(key, jc);
            } catch (JAXBException e1) {
                assert false : "Failed to create context!\n" + e1.toString();
            }
        }
        return jc;
    }

    /**
     * Creates a {@link Schema} from a collection of {@link SchemaModule}s, with a given
     * {@link LSResourceResolver} (probably obtained from
     * {@link #createResourceResolver(Set, ProgressIndicator)}. Any warnings or errors are
     * logged to the given {@link ProgressIndicator}.
     */
    @VisibleForTesting
    @NonNull
    public static Schema getSchema(
            final Collection<SchemaModule<?>> possibleModules,
            @Nullable final LSResourceResolver resourceResolver, final ProgressIndicator progress) {
        SchemaFactory sf =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        if (resourceResolver != null) {
            sf.setResourceResolver(resourceResolver);
        }
        sf.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                progress.logWarning("Warning while creating schema:", exception);
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                progress.logWarning("Error creating schema:", exception);

            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                progress.logWarning("Fatal error creating schema:", exception);
            }
        });

        List<StreamSource> sources = Lists.newArrayList();
        List<SchemaModule<?>.SchemaModuleVersion<?>> key = Lists.newArrayList();
        for (SchemaModule<?> module : possibleModules) {
            for (SchemaModule<?>.SchemaModuleVersion<?> version : module
                    .getNamespaceVersionMap()
                    .values()) {
                key.add(version);
                sources.add(new StreamSource(version.getXsd()));
            }
        }

        Map<LSResourceResolver, Schema> resolverSchemaCache = SCHEMA_CACHE.get(key);
        if (resolverSchemaCache == null) {
            resolverSchemaCache = Maps.newHashMap();
            SCHEMA_CACHE.put(key, resolverSchemaCache);
        }
        Schema schema = resolverSchemaCache.get(resourceResolver);
        if (schema == null) {
            try {
                schema = sf.newSchema(sources.toArray(new StreamSource[0]));
                resolverSchemaCache.put(resourceResolver, schema);
            }
            catch (SAXException e) {
                assert false : "Invalid schema found!";
            }
        }
        return schema;
    }

    /**
     * Use JAXB to create POJOs from the given XML.
     *
     * @param xml The XML to read. The stream will be closed after being read.
     * @param possibleModules The {@link SchemaModule}s that are available to parse the XML.
     * @param progress For logging.
     * @return The unmarshalled object.
     * @throws JAXBException if there is an error during unmarshalling.
     *     <p>TODO: maybe templatize and return a nicer type.
     */
    @Nullable
    public static Object unmarshal(
            @NonNull InputStream xml,
            @NonNull Collection<SchemaModule<?>> possibleModules,
            boolean strict,
            @NonNull ProgressIndicator progress)
            throws JAXBException {
        Unmarshaller u = setupUnmarshaller(possibleModules, strict, progress);
        SAXSource source = setupSource(xml, possibleModules, strict, progress);
        return ((JAXBElement) u.unmarshal(source)).getValue();
    }

    /**
     * Creates an {@link Unmarshaller} for the given {@link SchemaModule}s.
     *
     * @param possibleModules The schemas we should use to unmarshal.
     * @param strict Whether we should do strict validation.
     * @param progress For logging.
     */
    @NonNull
    private static Unmarshaller setupUnmarshaller(
            @NonNull Collection<SchemaModule<?>> possibleModules,
            boolean strict,
            @NonNull ProgressIndicator progress)
            throws JAXBException {
        JAXBContext context = getContext(possibleModules);
        Unmarshaller u = context.createUnmarshaller();
        u.setEventHandler(createValidationEventHandler(progress, strict));
        return u;
    }

    /**
     * Creates a {@link SAXSource} for the given input.
     *
     * @param xml             The xml input stream.
     * @param possibleModules Possible {@link SchemaModule}s that can describe the xml
     * @param strict          Whether we should do strict validation. Specifically in this case if
     *                        we should allow falling back to older schema versions if the xml uses
     *                        a newer one than we have access to.
     * @param progress        For logging.
     */
    @NonNull
    private static SAXSource setupSource(@NonNull InputStream xml,
            @NonNull Collection<SchemaModule<?>> possibleModules, boolean strict,
            @NonNull ProgressIndicator progress) throws JAXBException {
        SAXSource source = new SAXSource(new InputSource(xml));
        // Create the XMLFilter
        XMLFilter filter = new NamespaceFallbackFilter(possibleModules, strict, progress);

        // Set the parent XMLReader on the XMLFilter
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        XMLReader xr;
        try {
            SAXParser sp = spf.newSAXParser();
            xr = sp.getXMLReader();
            xr.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException | SAXException e) {
            // Shouldn't happen
            progress.logError("Error setting up parser", e);
            throw new JAXBException(e);
        }
        filter.setParent(xr);
        source.setXMLReader(filter);
        return source;
    }

    /**
     * Transform the given {@link JAXBElement} into xml, using JAXB and the schemas provided by the
     * given {@link SchemaModule}s.
     */
    public static void marshal(@NonNull JAXBElement element,
            @NonNull Collection<SchemaModule<?>> possibleModules,
            @NonNull OutputStream out, @Nullable LSResourceResolver resourceResolver,
            @NonNull ProgressIndicator progress) {
        JAXBContext context = getContext(possibleModules);
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setEventHandler(createValidationEventHandler(progress, true));
            Schema schema = getSchema(possibleModules, resourceResolver, progress);
            marshaller.setSchema(schema);
            marshaller.marshal(element, out);
            out.close();
        } catch (JAXBException | IOException e) {
            progress.logWarning("Error during marshal", e);
        }
    }

    /**
     * Creates a {@link ValidationEventHandler} that delegates logging to the given
     * {@link ProgressIndicator}.
     */
    @NonNull
    private static ValidationEventHandler createValidationEventHandler(
            @NonNull final ProgressIndicator progress, final boolean strict) {
        return event -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (event.getLinkedException() != null) {
                progress.logWarning(event.getMessage(), event.getLinkedException());
            } else {
                progress.logWarning(event.getMessage());
            }
            return !strict;
        };
    }

    private static class SchemaModuleResourceResolver implements LSResourceResolver {
        private final Set<SchemaModule<?>> mModules;
        private static DOMImplementationLS sLs;

        public SchemaModuleResourceResolver(Set<SchemaModule<?>> modules,
                ProgressIndicator progress) {
            mModules = modules;
            initLs(progress);
        }

        private static void initLs(ProgressIndicator progress) {
            if (sLs == null) {
                DOMImplementationRegistry registry;
                try {
                    registry = DOMImplementationRegistry.newInstance();
                    sLs = (DOMImplementationLS) registry.getDOMImplementation("LS");
                } catch (Exception e) {
                    progress.logError("Error during resolver creation: ", e);
                }
            }
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId,
                String systemId, String baseURI) {
            SchemaModule<?>.SchemaModuleVersion<?> version;
            for (SchemaModule<?> ext : mModules) {
                version = ext.getNamespaceVersionMap().get(namespaceURI);
                if (version != null) {
                    LSInput input = sLs.createLSInput();
                    input.setSystemId(version.getNamespace());
                    input.setByteStream(version.getXsd());
                    return input;
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SchemaModuleResourceResolver)) {
                return false;
            }
            SchemaModuleResourceResolver other = (SchemaModuleResourceResolver)obj;
            return other.mModules.equals(mModules);
        }

        @Override
        public int hashCode() {
            return mModules.hashCode();
        }
    }

    /**
     * {@link XMLFilter} that optionally maps namespaces newer than our latest known ones to
     * the latest one we understand.
     * For example, if we have SchemaModuleVersions with namespaces "foo/bar/01" and "foo/bar/02"
     * and we encounter a document with an element in namespace "foo/bar/03", this filter will
     * transform the namespace of that element to "foo/bar/02".
     */
    private static class NamespaceFallbackFilter extends XMLFilterImpl {

        private Map<String, SchemaModule<?>> mPrefixMap = Maps.newHashMap();
        private ProgressIndicator mProgress;
        private boolean mStrict;
        private Map<String, String> mNewToOldMap = Maps.newHashMap();

        public NamespaceFallbackFilter(
                @NonNull Collection<SchemaModule<?>> possibleModules, boolean strict,
                @NonNull ProgressIndicator progress) {
            for (SchemaModule<?> module : possibleModules) {
                mPrefixMap.put(module.getNamespacePrefix(), module);
            }
            mProgress = progress;
            mStrict = strict;
        }

        @Override
        public void startPrefixMapping(@Nullable String prefix, @Nullable String uri)
                throws SAXException {
            if (uri != null) {
                int lastSlash = uri.lastIndexOf('/') + 1;
                if (lastSlash > 0) {
                    String namespacePrefix = uri.substring(0, lastSlash);
                    try {
                        int version = Integer.parseInt(uri.substring(lastSlash));
                        SchemaModule<?> module = mPrefixMap.get(namespacePrefix);
                        if (module != null && module.getNamespaceVersionMap().size() < version) {
                            String oldUri = module.getLatestNamespace().intern();
                            mProgress.logWarning("Mapping new ns " + uri + " to old ns " + oldUri);
                            mNewToOldMap.put(uri, oldUri);
                            uri = oldUri;
                        }
                    } catch (NumberFormatException e) {
                        // nothing, just don't do any substitution.
                    }
                }
            }
            super.startPrefixMapping(prefix, uri);
        }

        @Override
        public void startElement(@Nullable String uri, @Nullable String localName,
                @Nullable String qName, @Nullable Attributes atts)
                throws SAXException {
            AttributesImpl newAtts = new AttributesImpl(atts);
            if (!mStrict && uri != null && mNewToOldMap.containsKey(uri)) {
                uri = mNewToOldMap.get(uri);
            }
            super.startElement(uri, localName, qName, newAtts);
        }

    }
}
