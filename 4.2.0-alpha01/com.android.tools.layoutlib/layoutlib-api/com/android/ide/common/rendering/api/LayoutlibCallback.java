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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.net.URL;

/**
 * Intermediary class implementing parts of both the old and new ProjectCallback from the LayoutLib
 * API.
 *
 * <p>Even newer LayoutLibs use this directly instead of the the interface. This allows the
 * flexibility to add newer methods without having to update {@link Bridge#API_CURRENT LayoutLib API
 * version}.
 */
@SuppressWarnings("unused")
public abstract class LayoutlibCallback implements XmlParserFactory {

    public enum ViewAttribute {
        TEXT(String.class),
        IS_CHECKED(Boolean.class),
        SRC(URL.class),
        COLOR(Integer.class);

        private final Class<?> mClass;

        ViewAttribute(Class<?> theClass) {
            mClass = theClass;
        }

        public Class<?> getAttributeClass() {
            return mClass;
        }
    }

    /**
     * Loads a custom class with the given constructor signature and arguments.
     *
     * <p>Despite the name, the method is used not just for views (android.view.View), but
     * potentially any class in the project's namespace. However, when the method is used for
     * loading non-view classes the error messages reported may not be ideal, since the the IDE may
     * assume those classes to be a view and try to use a different constructor or replace it with a
     * MockView.
     *
     * <p>This is done so that LayoutLib can continue to work on older versions of the IDE. Newer
     * versions of LayoutLib should call {@link LayoutlibCallback#loadClass(String, Class[],
     * Object[])} in such a case.
     *
     * @param name the fully qualified name of the class.
     * @param constructorSignature the signature of the class to use
     * @param constructorArgs the arguments to use on the constructor
     * @return a newly instantiated object.
     */
    @Nullable
    public abstract Object loadView(
            @NonNull String name, @NonNull Class[] constructorSignature, Object[] constructorArgs)
            throws Exception;

    /** Finds the resource with a given id. */
    @Nullable
    public abstract ResourceReference resolveResourceId(int id);

    /**
     * Returns the numeric id for the given resource, potentially generating a fresh ID.
     *
     * <p>Calling this method for equal references will always produce the same result.
     */
    public abstract int getOrGenerateResourceId(@NonNull ResourceReference resource);

    /**
     * Returns a custom parser for a value
     *
     * @param layoutResource Layout or a value referencing an _aapt attribute.
     * @return returns a custom parser or null if no custom parsers are needed.
     */
    @Nullable
    public abstract ILayoutPullParser getParser(@NonNull ResourceValue layoutResource);

    /**
     * Returns the value of an item used by an adapter.
     *
     * @param adapterView the {@link ResourceReference} for the adapter view info.
     * @param adapterCookie the view cookie for this particular view.
     * @param itemRef the {@link ResourceReference} for the layout used by the adapter item.
     * @param fullPosition the position of the item in the full list.
     * @param positionPerType the position of the item if only items of the same type are
     *     considered. If there is only one type of items, this is the same as
     *     <var>fullPosition</var>.
     * @param fullParentPosition the full position of the item's parent. This is only valid if the
     *     adapter view is an ExpandableListView.
     * @param parentPositionPerType the position of the parent's item, only considering items of the
     *     same type. This is only valid if the adapter view is an ExpandableListView. If there is
     *     only one type of items, this is the same as <var>fullParentPosition</var>.
     * @param viewRef the {@link ResourceReference} for the view we're trying to fill.
     * @param viewAttribute the attribute being queried.
     * @param defaultValue the default value for this attribute. The object class matches the class
     *     associated with the {@link ViewAttribute}.
     * @return the item value or null if there's no value.
     * @see ViewAttribute#getAttributeClass()
     */
    @Nullable
    public Object getAdapterItemValue(
            ResourceReference adapterView,
            Object adapterCookie,
            ResourceReference itemRef,
            int fullPosition,
            int positionPerType,
            int fullParentPosition,
            int parentPositionPerType,
            ResourceReference viewRef,
            ViewAttribute viewAttribute,
            Object defaultValue) {
        return null;
    }

    /**
     * Returns an adapter binding for a given adapter view. This is only called if {@link
     * SessionParams} does not have an {@link AdapterBinding} for the given {@link
     * ResourceReference} already.
     *
     * @param adapterViewRef the reference of adapter view to return the adapter binding for.
     * @param adapterCookie the view cookie for this particular view.
     * @param viewObject the view object for the adapter.
     * @return an adapter binding for the given view or null if there's no data.
     */
    @Nullable
    public abstract AdapterBinding getAdapterBinding(
            ResourceReference adapterViewRef, Object adapterCookie, Object viewObject);

    /**
     * Returns a callback for Action Bar information needed by the Layout Library. The callback
     * provides information like the menus to add to the Action Bar.
     *
     * @since API 11
     */
    public abstract ActionBarCallback getActionBarCallback();

    /**
     * Like {@link #loadView(String, Class[], Object[])}, but intended for loading classes that may
     * not be custom views.
     *
     * @param name className in binary format (see {@link ClassLoader})
     * @return an new instance created by calling the given constructor.
     * @throws ClassNotFoundException any exceptions thrown when creating the instance is wrapped in
     *     a ClassNotFoundException.
     * @since API 15
     */
    public Object loadClass(
            @NonNull String name,
            @Nullable Class[] constructorSignature,
            @Nullable Object[] constructorArgs)
            throws ClassNotFoundException {
        try {
            return loadView(name, constructorSignature, constructorArgs);
        }
        catch (ClassNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ClassNotFoundException(name + " not found.", e);
        }
    }

    /**
     * A callback to query arbitrary data. This is similar to {@link RenderParams#setFlag(SessionParams.Key,
     * Object)}. The main difference is that when using this, the IDE doesn't have to compute the
     * value in advance and thus may save on some computation.
     * @since API 15
     */
    @Nullable
    public <T> T getFlag(@NonNull SessionParams.Key<T> key) {
        return null;
    }

    /**
     * Finds a custom class in the project.
     *
     * <p>Like {@link #loadClass(String, Class[], Object[])}, but doesn't instantiate an object and
     * just returns the class found.
     *
     * @param name className in binary format. (see {@link ClassLoader}.
     * @since API 15
     */
    @NonNull
    public Class<?> findClass(@NonNull String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name + " not found.");
    }

    /**
     * Checks if the class was previously loaded in the project. Does not load the class.
     *
     * @param name className in binary format. (see {@link ClassLoader}.
     * @return if class was loaded or not
     */
    public boolean isClassLoaded(@NonNull String name) {
        return false;
    }

    /**
     * Returns an optional {@link ResourceNamespace.Resolver} that knows namespace prefixes assumed
     * to be declared in every resource file.
     *
     * <p>For backwards compatibility, in non-namespaced projects this contains the "tools" prefix
     * mapped to {@link ResourceNamespace#TOOLS}. Before the IDE understood resource namespaces,
     * this prefix was used for referring to sample data, even if the user didn't define the "tools"
     * prefix using {@code xmlns:tools="..."}.
     *
     * <p>In namespaced projects this method returns an empty resolver, which means sample data
     * won't work without an explicit definition of a namespace prefix for the {@link
     * ResourceNamespace#TOOLS} URI.
     */
    @NonNull
    public ResourceNamespace.Resolver getImplicitNamespaces() {
        return ResourceNamespace.Resolver.EMPTY_RESOLVER;
    }

    /** Returns true if the module depends on android.support.v7.appcompat. */
    public boolean hasLegacyAppCompat() {
        return false;
    }

    /** Returns true if the module depends on androidx.appcompat. */
    public boolean hasAndroidXAppCompat() {
        return false;
    }

    /** Returns true if the module uses namespaced resources. */
    public boolean isResourceNamespacingRequired() {
        return false;
    }

    /** Logs an error message to the Studio error log. */
    public void error(@NonNull String message, @NonNull String... details) {}

    /** Logs an error message to the Studio error log. */
    public void error(@NonNull String message, @Nullable Throwable t) {}

    /** Logs an error message to the Studio error log. */
    public void error(@NonNull Throwable t) {}

    /** Logs a warning message to the Studio log. */
    public void warn(@NonNull String message, @Nullable Throwable t) {}

    /** Logs a warning message to the Studio log. */
    public void warn(@NonNull Throwable t) {}
}
