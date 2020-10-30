/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.JvmWideVariable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.invocation.Gradle;

/**
 * A singleton object that exists across class loaders, across builds, and is specific to a plugin
 * version.
 *
 * <p>The singleton object is created when the first build starts and continues to live across
 * builds until the JVM exits. The object is unique (per plugin version) across class loaders even
 * if the plugin is loaded multiple times. If different plugin versions are loaded, there will be
 * multiple {@code BuildSessionImpl} objects corresponding to each of the plugin versions in the
 * JVM.
 *
 * <p>Here, a build refers to the entire Gradle build, which includes included builds in the case of
 * composite builds. Note that the Gradle daemon never executes two builds at the same time,
 * although it may execute sub-builds (for sub-projects) or included builds in parallel.
 *
 * <p>To ensure proper usage, the {@link #initialize(Gradle)} method must be called immediately
 * whenever a new build starts (for each plugin version). It may be called more than once in a
 * build; if so, subsequent calls simply return immediately since the build has already started.
 */
@ThreadSafe
public final class BuildSessionImpl implements BuildSession {

    /**
     * A {@link BuildSession} instance that is either the actual {@link BuildSessionImpl} singleton
     * object or a proxy to that object if the object's class is loaded by a different class loader.
     */
    @NonNull
    private static final BuildSession singleton =
            createBuildSessionSingleton(Version.ANDROID_GRADLE_PLUGIN_VERSION);

    @NonNull
    @VisibleForTesting
    static BuildSession createBuildSessionSingleton(@NonNull String pluginVersion) {
        Object buildSessionSingleton =
                Verify.verifyNotNull(
                        new JvmWideVariable<>(
                                        BuildSessionImpl.class.getName(),
                                        BuildSessionImpl.class.getSimpleName(),
                                        pluginVersion,
                                        TypeToken.of(Object.class),
                                        BuildSessionImpl::new)
                                .get());

        if (buildSessionSingleton instanceof BuildSession) {
            return (BuildSession) buildSessionSingleton;
        } else {
            return (BuildSession)
                    Proxy.newProxyInstance(
                            BuildSession.class.getClassLoader(),
                            new Class[] {BuildSession.class},
                            new DelegateInvocationHandler(buildSessionSingleton));
        }
    }

    /**
     * Returns a {@link BuildSession} instance that is either the actual {@link BuildSessionImpl}
     * singleton object or a proxy to that object if the object's class is loaded by a different
     * class loader.
     */
    @NonNull
    public static BuildSession getSingleton() {
        return singleton;
    }

    /** State of the build. */
    private enum BuildState {

        /** The build has started. */
        STARTED,

        /**
         * The build is almost finished except that the actions registered to be executed at the end
         * of the build have not yet been executed.
         */
        FINISHING,

        /** The build is finished. */
        FINISHED,
    }

    /** The state of the current build. */
    @GuardedBy("this")
    @NonNull
    private BuildState buildState = BuildState.FINISHED;

    /** The actions that have been executed immediately. */
    @GuardedBy("this")
    @NonNull
    private Set<String> executedActions = new HashSet<>();


    /** The actions to be executed at the end of the current build. */
    @GuardedBy("this")
    @NonNull
    private LinkedHashMap<String, Runnable> buildFinishedActions = new LinkedHashMap<>();

    @Override
    public synchronized void initialize(@NonNull Gradle gradle) {
        // If the build has already started, return immediately
        if (buildState == BuildState.STARTED) {
            return;
        }

        // If buildState is FINISHING, it may have been caused by a Ctrl-C, but we won't deal with
        // it for now and will consider it as FINISHED.
        // If buildState is FINISHED, let's start a new build.
        buildState = BuildState.STARTED;

        // Register a handler to execute at the end of the build. We need to use the "root" Gradle
        // object to get to the end of both regular builds and composite builds.
        Gradle rootGradle = gradle;
        //noinspection ConstantConditions
        while (rootGradle.getParent() != null) {
            rootGradle = rootGradle.getParent();
        }
        rootGradle.addBuildListener(
                new BuildAdapter() {
                    @Override
                    public void buildFinished(@NonNull BuildResult buildResult) {
                        BuildSessionImpl.this.buildFinished();
                    }
                });
    }

    @Override
    public synchronized void executeOnce(
            @NonNull String actionGroup, @NonNull String actionName, @NonNull Runnable action) {
        Preconditions.checkState(buildState == BuildState.STARTED);

        String actionId = actionGroup + ":" + actionName;
        if (!executedActions.contains(actionId)) {
            executedActions.add(actionId);
            action.run();
        }
    }

    @Override
    public synchronized void executeOnceWhenBuildFinished(
            @NonNull String actionGroup, @NonNull String actionName, @NonNull Runnable action) {
        Preconditions.checkState(buildState == BuildState.STARTED);

        buildFinishedActions.putIfAbsent(actionGroup + ":" + actionName, action);
    }

    private synchronized void buildFinished() {
        Preconditions.checkState(buildState == BuildState.STARTED);

        buildState = BuildState.FINISHING;
        try {
            buildFinishedActions.values().forEach(Runnable::run);
        } finally {
            // If an exception occurred, it may affect the next build, but we won't deal with it
            // for now
            executedActions.clear();
            buildFinishedActions.clear();
            buildState = BuildState.FINISHED;
        }
    }

    @Override
    @SuppressWarnings("GuardedBy")
    public String toString() {
        //noinspection FieldAccessNotGuarded
        return MoreObjects.toStringHelper(this).add("buildState", buildState.name()).toString();
    }

    /**
     * Invocation handler that delegates method calls to another object. This is part of the pattern
     * to create and access a "true" singleton (object that is unique across class loaders).
     */
    @Immutable
    @VisibleForTesting
    static final class DelegateInvocationHandler implements InvocationHandler {

        @NonNull private final Object delegate;

        public DelegateInvocationHandler(@NonNull Object delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("unused") // Used via reflection in tests
        @NonNull
        public Object getDelegate() {
            return delegate;
        }

        @Nullable
        @Override
        public Object invoke(@NonNull Object proxy, @NonNull Method method, @NonNull Object[] args)
                throws Throwable {
            return delegate.getClass()
                    .getMethod(method.getName(), method.getParameterTypes())
                    .invoke(delegate, args);
        }
    }
}
