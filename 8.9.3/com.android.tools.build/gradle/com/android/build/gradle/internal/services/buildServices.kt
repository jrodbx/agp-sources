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

package com.android.build.gradle.internal.services

import com.android.Version
import com.android.build.gradle.internal.dependency.AgpVersionCompatibilityRule
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

/** Registers and configures the build service with the specified type. */
abstract class ServiceRegistrationAction<ServiceT, ParamsT>(
    protected val project: Project,
    private val buildServiceClass: Class<ServiceT>,
    private val maxParallelUsages: Int? = null,
    private val name: String = getBuildServiceName(buildServiceClass),
) where ServiceT : BuildService<ParamsT>, ParamsT : BuildServiceParameters {
    open fun execute(): Provider<ServiceT> {
        return project.gradle.sharedServices.registerIfAbsent(
            name,
            buildServiceClass
        ) { buildServiceSpec ->
            @Suppress("UNNECESSARY_SAFE_CALL")
            buildServiceSpec.parameters?.let { params -> configure(params) }
            maxParallelUsages?.let { buildServiceSpec.maxParallelUsages.set(it) }
        }
    }

    abstract fun configure(parameters: ParamsT)
}

/** Returns the build service with the specified type. Prefer reified [getBuildService] to this method. */
fun <ServiceT : BuildService<ParamsT>, ParamsT: BuildServiceParameters> getBuildService(
    buildServiceRegistry: BuildServiceRegistry,
    buildServiceClass: Class<ServiceT>
): Provider<ServiceT> {
    val serviceName = getBuildServiceName(buildServiceClass)
    /**
     * We use registerIfAbsent in order to ensure locking when accessing build services. Because of
     * https://github.com/gradle/gradle/issues/18587, Gradle ensures thread safety only for
     * service registration. Using [BuildServiceRegistry.getRegistrations] to access build services
     * may cause problems such as http://b/238336467.
     */
    return buildServiceRegistry.registerIfAbsent(serviceName, buildServiceClass) {
        throw IllegalStateException("Service $serviceName is not registered.")
    }
}

/** Returns the build service of [ServiceT] type. */
inline fun <reified ServiceT : BuildService<ParamsT>, ParamsT: BuildServiceParameters> getBuildService(buildServiceRegistry: BuildServiceRegistry): Provider<ServiceT> {
    return getBuildService(buildServiceRegistry, ServiceT::class.java)
}

/**
 * Get build service name that works even if build service types come from different class loaders.
 * If the service name is the same, and some type T is defined in two class loaders L1 and L2. E.g.
 * this is true for composite builds and other project setups (see b/154388196).
 *
 * Registration of service may register (T from L1) or (T from L2). This means that querying it with
 * T from other class loader will fail at runtime. This method makes sure both T from L1 and T from
 * L2 will successfully register build services.
 */
fun getBuildServiceName(type: Class<*>): String = type.name + "_" + perClassLoaderConstant

/** Used to get unique build service name. Each class loader will initialize its own version. */
private val perClassLoaderConstant = UUID.randomUUID().toString()

/**
 * Registers a global (cross-classloader) [BuildService] with Gradle if it is not yet registered.
 *
 * ### Scope & lifetime
 *
 * A global [BuildService] has the scope of the [Gradle] object. Note that in a composite build,
 * there can be multiple [Gradle] objects within the JVM, and therefore multiple global
 * [BuildService]s of the same type.
 *
 * A global (or non-global) [BuildService] will be available for GC when it is no longer used. This
 * usually happens at the end of the build. Note that with configuration cache enabled, a
 * [BuildService] created in the configuration phase may be discarded at the end of the
 * configuration phase, and recreated in the execution phase if needed (also, a [BuildService] may
 * not be created if the configuration phase is skipped).
 *
 * ### API requirements
 *
 * Since types across classloaders are incompatible, the API of a global [BuildService] has the
 * following requirements:
 *
 *   1. The global [BuildService] must define and implement its own interface ([ServiceInterface]).
 *   This is so that we can use a [Proxy] to access the global [BuildService] from a different
 *   classloader through reflection (see [createProxy]). Users of the global [BuildService] can only
 *   interact with it through this interface (the returned value of
 *   [GlobalServiceRegistrationAction.execute] is a [Provider] of [ServiceInterface], not
 *   [ServiceImpl]).
 *
 *   2. Methods in the global [BuildService]'s interface must have parameter types and return types
 *   that belong to one of the following categories:
 *
 *     a) They are classloader-agnostic: JVM types, Java core library types, or Gradle types. They
 *     must not be types defined in AGP or other Gradle plugins, which are classloader-specific.
 *
 *     b) They are interfaces. Interfaces are acceptable for the same reason as requirement 1.
 *
 *     c) They implement [Serializable]. This allows us to pass copies of objects across
 *     classloaders when interacting with the build service. Note that the types must be *immutable*
 *     types as we are passing the objects' copies, not their references. It's also better if the
 *     objects are not too large.
 *
 *   3. Different [Project]s must use the same version of the AGP, so that it is safe to convert
 *   corresponding types across classloaders. (This is currently enforced by
 *   [AgpVersionCompatibilityRule] and also by [checkSameAgpVersion].) This requirement does not
 *   apply to different [Gradle] builds in a composite build -- see "Scope & lifetime" section.
 *
 * ### Performance
 *
 * There could be some performance overhead with reflection (method look-ups in particular) and/or
 * copying objects if there are a lot of method calls to the global [BuildService].
 *
 * ### Alternative
 *
 * If the previous requirements cannot be met or if the performance overhead is high, an alternative
 * is to use non-global, classloader-specific [BuildService]s (see [ServiceRegistrationAction]).
 *
 * ### Thread safety
 *
 * A global (or non-global) [BuildService] contains shared resources, so access to them needs to be
 * thread safe. In addition, it is possible that multiple [BuildService]s within the same or
 * different JVMs (see "Scope & lifetime" section) can access an external resource concurrently
 * (e.g., writing to the same file/directory); in that case, access to those resources will also
 * need to be thread-safe/process-safe, potentially by using some OS-level locking such as
 * [java.nio.channels.FileLock].
 */
abstract class GlobalServiceRegistrationAction<ServiceInterface, ServiceImpl, Params>(
    protected val project: Project,
    private val buildServiceInterface: Class<ServiceInterface>,
    private val buildServiceImplClass: Class<ServiceImpl>,
) where ServiceInterface : Any, ServiceImpl : BuildService<Params>, Params : BuildServiceParameters {

    init {
        check(buildServiceInterface.isInterface && buildServiceInterface.isAssignableFrom(buildServiceImplClass))
    }

    fun execute(): Provider<ServiceInterface> {
        val globalService: Provider<ServiceImpl> = project.gradle.sharedServices
            .registerIfAbsent(buildServiceImplClass.name, buildServiceImplClass) { spec ->
                @Suppress("UNNECESSARY_SAFE_CALL")
                spec.parameters?.let { configure(it) }
            }
        return globalService.map { service ->
            checkSameAgpVersion(service.javaClass.classLoader, buildServiceInterface.classLoader)
            createProxy(service, buildServiceInterface)
        }
    }

    abstract fun configure(parameters: Params)
}

private const val UNKNOWN_VERSION = "UNKNOWN_VERSION"

/** Checks that the AGP version is the same across classloaders.*/
private fun checkSameAgpVersion(classLoader1: ClassLoader, classLoader2: ClassLoader) {
    fun getVersion(classLoader: ClassLoader): String = try {
        classLoader.loadClass(Version::class.java.name).getField("ANDROID_GRADLE_PLUGIN_VERSION").get(null) as String
    } catch (e: Throwable) {
        UNKNOWN_VERSION
    }
    val version1 = getVersion(classLoader1)
    val version2 = getVersion(classLoader2)
    check(version1 != UNKNOWN_VERSION && version1 == version2) {
        "Using different versions of the Android Gradle plugin ($version1, $version2) in the same build is not allowed."
    }
}

/**
 * Returns a [Proxy] of [objectFromAnotherClassloader] if [objectFromAnotherClassloader]'s type is
 * loaded from a different classloader than [targetInterface] (the interface of the returned proxy).
 *
 * If [objectFromAnotherClassloader]'s type is loaded from the same classloader as
 * [targetInterface], then this method will simply return it without creating a proxy.
 *
 * @param objectFromAnotherClassloader object which implements [T]' where [T]' is the same as [T]
 *    except that it may be loaded from a different classloader
 * @param targetInterface the [Class] instance of interface [T]
 */
private fun <T> createProxy(objectFromAnotherClassloader: Any, targetInterface: Class<T>): T {
    // If the object itself is a Proxy, simplify it first by getting its target object
    if (objectFromAnotherClassloader is Proxy) {
        val invocationHandler = Proxy.getInvocationHandler(objectFromAnotherClassloader)
        if (invocationHandler is CrossClassloaderInvocationHandler) {
            // The way `Proxy`s are created ensures that they will not form a loop, so the recursive
            // call below will always terminate.
            return createProxy(invocationHandler.targetObject, targetInterface)
        }
    }

    @Suppress("UNCHECKED_CAST")
    return when (objectFromAnotherClassloader.javaClass.classLoader) {
        targetInterface.classLoader -> objectFromAnotherClassloader
        else -> Proxy.newProxyInstance(
            targetInterface.classLoader,
            arrayOf(targetInterface),
            CrossClassloaderInvocationHandler(objectFromAnotherClassloader)
        )
    } as T
}

/**
 * [InvocationHandler] that delegates method calls to [targetObject] whose type may be loaded from a
 * different classloader.
 */
private class CrossClassloaderInvocationHandler(val targetObject: Any) : InvocationHandler {

    override fun invoke(originObject: Any, originMethod: Method, originArgs: Array<out Any?>?): Any? {
        // TODO: Add caching to optimize method lookup
        val targetMethod = targetObject.javaClass.methods.singleOrNull {
            originMethod.name == it.name
                    && originMethod.parameterTypes.map(Class<*>::getName) == it.parameterTypes.map(Class<*>::getName)
                    && originMethod.returnType.name == it.returnType.name
        } ?: error("Unable to find method `${originMethod.name}` in class `${targetObject.javaClass.name}`")

        val targetArgs = originMethod.parameterTypes.mapIndexed { index, originArgType ->
            val targetArgType = targetMethod.parameterTypes[index]!!
            val originArg = originArgs!![index]
            when {
                originArg == null -> null
                originArgType == targetArgType -> originArg
                originArgType.isInterface -> createProxy(originArg, targetArgType)
                originArg is Serializable -> copyObject(originArg, targetArgType)
                else -> error("Parameter type `${originArgType.name}` is not classloader-agnostic, is not an interface, and does not implement ${Serializable::class.java.name}")
            }
        }

        val targetReturnValue = targetMethod.invoke(targetObject, *targetArgs.toTypedArray())

        return when {
            targetReturnValue == null -> null
            targetMethod.returnType == originMethod.returnType -> targetReturnValue
            targetMethod.returnType.isInterface -> createProxy(targetReturnValue, originMethod.returnType)
            targetReturnValue is Serializable -> copyObject(targetReturnValue, originMethod.returnType)
            else -> error("Return type `${targetMethod.returnType.name}` is not classloader-agnostic, is not an interface, and does not implement ${Serializable::class.java.name}")
        }
    }
}

/**
 * Creates a copy of [objectFromAnotherClassloader] where [targetType] is type of the returned copy.
 *
 * @param objectFromAnotherClassloader object with type [T]' where [T]' is the same as [T] except
 *    that it may be loaded from a different classloader
 * @param targetType the [Class] instance of type [T], it must implement [Serializable]
 */
private fun <T> copyObject(objectFromAnotherClassloader: Any, targetType: Class<T>): T {
    val serializedObject = ByteArrayOutputStream().run {
        ObjectOutputStream(this).use {
            it.writeObject(objectFromAnotherClassloader)
        }
        toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    return CrossClassloaderObjectInputStream(serializedObject.inputStream(), targetType).use {
        it.readObject()
    } as T
}

/**
 * [ObjectInputStream] which overrides the [resolveClass] method to allow creating a copy of an
 * object from another classloader (see [copyObject]).
 */
private class CrossClassloaderObjectInputStream(
    inputStream: InputStream,
    private val targetType: Class<*>
) : ObjectInputStream(inputStream) {

    override fun resolveClass(objectStreamClass: ObjectStreamClass): Class<*> {
        return if (objectStreamClass.name == targetType.name) {
            targetType
        } else {
            super.resolveClass(objectStreamClass)
        }
    }
}
