/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.AgpDslLockedException
import com.android.build.gradle.internal.dsl.Lockable
import com.android.utils.usLocaleDecapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.gradle.api.logging.Logging
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * A generator of part of the implementation of the AGP DSL
 *
 * Given an abstract class, calling [decorate] will return a generated subclass which has
 * anything that was abstract and is included in the [supportedPropertyTypes] implemented.
 */
class DslDecorator(supportedPropertyTypes: List<SupportedPropertyType> = AGP_SUPPORTED_PROPERTY_TYPES) {

    private val supportedPropertyTypes: Map<Type, SupportedPropertyType> = supportedPropertyTypes.associateBy { it.type }

    private val cache: LoadingCache<Class<*>, Class<*>> =
        CacheBuilder.newBuilder().build(object : CacheLoader<Class<*>, Class<*>>() {
            override fun load(dslClass: Class<*>): Class<*> {
                val stopWatch = Stopwatch.createStarted()
                try {
                    return decorateDslClassImpl(dslClass)
                } finally {
                    val logger = Logging.getLogger(DslDecorator::class.java)
                    if (logger.isDebugEnabled) {
                        logger.debug("Class {} instrumented in {}", dslClass, stopWatch.elapsed())
                    }
                }
            }
        })

    fun <T : Any> decorate(dslClass: KClass<T>): Class<out T> = decorate(dslClass.java)

    fun <T : Any> decorate(dslClass: Class<T>): Class<out T> {
        @Suppress("UNCHECKED_CAST") // This is safe as the generator generates a subclass
        return cache.get(dslClass) as Class<out T>
    }

    internal fun <T : Any> decorateDslClassImpl(dslClass: Class<T>): Class<out T> {

        val dslClassType = Type.getType(dslClass)
        val generatedClass =
            Type.getType(dslClassType.descriptor.removeSuffix(";") + "\$AgpDecorated;")

        try {
            @Suppress("UNCHECKED_CAST")
            return dslClass.classLoader.loadClass(generatedClass.className) as Class<out T>
        } catch (ignored: ClassNotFoundException) {
            // Define the class
        }

        val lockable = Lockable::class.java.isAssignableFrom(dslClass)

        val classWriter =
            ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

        val isInterface = dslClass.isInterface
        val generatedClassSuperClass = if(isInterface) OBJECT_TYPE else dslClassType

        classWriter.visit(
            Opcodes.V1_8, Opcodes.ACC_PUBLIC,
            generatedClass.internalName,
            null,
            generatedClassSuperClass.internalName,
            if (isInterface) arrayOf(dslClassType.internalName) else arrayOf()
        )

        val abstractProperties = findAbstractProperties(dslClass)

        val constructors = (if (isInterface) Any::class.java else dslClass).declaredConstructors
        for (constructor in constructors) {
            val method = Method.getMethod(constructor)
            val inject = constructor.getDeclaredAnnotation(Inject::class.java) != null
            if (method.argumentTypes.isNotEmpty() && !inject) {
                // Gradle only looks at constructors with arguments if they are marked with @Inject.
                continue
            }
            GeneratorAdapter(constructor.modifiers, method, null, null, classWriter).apply {
                if (inject) {
                    visitAnnotation(INJECT_TYPE, true).visitEnd()
                }
                // super(...args...)
                visitCode()
                loadThis()
                loadArgs()
                invokeConstructor(generatedClassSuperClass, method)
                for (property in abstractProperties) {
                    when (val type = property.supportedPropertyType) {
                        is SupportedPropertyType.Val -> {
                            // field = new ImplType("propertyName")
                            loadThis()
                            newInstance(type.implementationType)
                            dup()
                            visitLdcInsn(property.name)
                            invokeConstructor(type.implementationType, LOCKABLE_CONSTRUCTOR)
                            putField(
                                generatedClass,
                                property.backingFieldName,
                                type.implementationType
                            )
                        }
                        is SupportedPropertyType.Var -> { /* Defaults to field default value */
                        }
                    }
                }
                returnValue()
                endMethod()
            }
        }
        if (lockable) {
            createLockField(classWriter)
        }
        for (field in abstractProperties) {
            createField(classWriter, field)
        }

        for (field in abstractProperties) {
            createFieldBackedGetters(classWriter, generatedClass, field)
            if (field.settersToGenerate.isNotEmpty()) {
                createFieldBackedSetters(classWriter, generatedClass, field, lockable)
            }
        }

        if (lockable) {
            createLockMethod(classWriter, generatedClass, abstractProperties)
        }

        classWriter.visitEnd()

        return lookupDefineClass(dslClass, classWriter.toByteArray())
    }

    @VisibleForTesting
    internal interface ClassInfoVisitor {
        fun method(method: java.lang.reflect.Method)
    }

    class CollectingInfoVisitor: ClassInfoVisitor {
        val getters = mutableMapOf<String, MutableList<java.lang.reflect.Method>>()
        val setters = mutableMapOf<String, MutableList<java.lang.reflect.Method>>()

        private fun MutableMap<String, MutableList<java.lang.reflect.Method>>.recordMethod(
            name: String, method: java.lang.reflect.Method) {
            getOrPut(name.usLocaleDecapitalize()) { mutableListOf() }.add(method)
        }

        override fun method(method: java.lang.reflect.Method) {
            val modifiers = method.modifiers
            if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)) return
            if (method.getAnnotation(Inject::class.java) != null) return
            when {
                method.name.startsWith("get") -> {
                    if (method.parameterCount != 0) return
                    getters.recordMethod(method.name.removePrefix("get"), method)
                }
                method.name.startsWith("set") -> {
                    if (method.returnType != Void.TYPE) return
                    if (method.parameterCount != 1) return
                    setters.recordMethod(method.name.removePrefix("set"), method)
                }
            }
        }
    }


    internal fun visitClass(theClass: Class<*>, visitor: ClassInfoVisitor) {
        val queue = ArrayDeque<Class<*>>()
        var c = theClass
        // First visit all superclasses, then interfaces.
        while (c != Any::class.java) { // Ignore Object
            queue.add(c)
            c = c.superclass ?: break // Interfaces don't have superclasses.
        }
        val seen = mutableSetOf<Class<*>>()
        while (true) {
            val current = queue.pollFirst() ?: break
            if (!seen.add(current)) {
                continue
            }
            for (method in current.declaredMethods) {
                visitor.method(method)
            }
            queue.addAll(current.interfaces)
        }
    }

    private class ManagedProperty(
        val name: String,
        val backingFieldName: String,
        val supportedPropertyType: SupportedPropertyType,
        val access: Int,
        val gettersToGenerate: Collection<Method>,
        val settersToGenerate: Collection<Method>,
    )

    private fun findAbstractProperties(dslClass: Class<*>): List<ManagedProperty> {

        val visitor = CollectingInfoVisitor()
        visitClass(dslClass, visitor)

        return visitor.getters.mapNotNull { (propertyName, getters) ->
            if (getters.any { !Modifier.isAbstract(it.modifiers) }) {
                // Implemented already
                return@mapNotNull null
            }

            val getterReturnTypes: MutableSet<Type> = mutableSetOf()
            var supportedPropertyType: SupportedPropertyType? = null
            var modifiers: Int = 0

            for (getter in getters) {
                val returnType = Type.getReturnType(getter)
                getterReturnTypes += returnType
                val getterSupportedPropertyType = supportedPropertyTypes[returnType] ?: continue
                if (supportedPropertyType == null) {
                    supportedPropertyType = getterSupportedPropertyType
                    // Take the modifiers from the first getter found,
                    // as it might be public overriding a protected getter in a superclass.
                    modifiers = notAbstract(getter.modifiers)
                }
                // And check that all the types are consistent.
                if (getterSupportedPropertyType != supportedPropertyType) {
                    throw IllegalStateException("Invalid abstract property '$propertyName' - ambiguous property mapping to multiple supported property types - $getterSupportedPropertyType & $supportedPropertyType")
                }
            }

            if (supportedPropertyType == null) {
                // Not a supported property type
                return@mapNotNull null
            }

            val setters = visitor.setters[propertyName]?.filter { Modifier.isAbstract(it.modifiers) } ?: listOf()
            ManagedProperty(
                propertyName,
                "__$propertyName",
                supportedPropertyType,
                modifiers,
                getters.asSequence().map { Method.getMethod(it) }.toSet(),
                setters.asSequence().map { Method.getMethod(it) }.toSet()
            )
        }
    }


    private fun <T> lookupDefineClass(originalClass: Class<T>, bytes: ByteArray): Class<out T> {
        val lookup = privateLookupInMethod.invoke(null, originalClass, MethodHandles.lookup()) as MethodHandles.Lookup
        @Suppress("UNCHECKED_CAST") return lookupDefineClassMethod.invoke(lookup, bytes) as Class<out T>
    }

    private fun createLockField(classWriter: ClassWriter) {
        classWriter.visitField(
            Opcodes.ACC_PRIVATE,
            LOCK_FIELD_NAME,
            Type.BOOLEAN_TYPE.descriptor,
            "",
            false
        ).visitEnd()
    }

    private fun createLockMethod(
        classWriter: ClassWriter,
        generatedClass: Type,
        abstractProperties: List<ManagedProperty>
    ) {
        GeneratorAdapter(Modifier.PUBLIC or Modifier.FINAL, Method.getMethod("void lock()"), null, null, classWriter).apply {
            // this.__lock = true;
            loadThis()
            push(true)
            putField(generatedClass, LOCK_FIELD_NAME, Type.BOOLEAN_TYPE)
            for (abstractProperty in abstractProperties) {
                val type = abstractProperty.supportedPropertyType
                if (type is SupportedPropertyType.Val) {
                    // this.__managedField.lock();
                    loadThis()
                    getField(generatedClass, abstractProperty.backingFieldName, type.implementationType)
                    invokeVirtual(type.implementationType, LOCK_METHOD)
                }
            }
            returnValue()
            endMethod()
        }
    }

    private fun createField(
        classWriter: ClassWriter,
        managedProperty: ManagedProperty,
    ) {
        classWriter.visitField(
            Opcodes.ACC_PRIVATE,
            managedProperty.backingFieldName,
            managedProperty.supportedPropertyType.implementationType.descriptor,
            "",
            null
        ).visitEnd()
    }

    private fun createFieldBackedSetters(
        classWriter: ClassWriter,
        generatedClass: Type,
        property: ManagedProperty,
        lockable: Boolean
    ) {
        for (setter in property.settersToGenerate) {
            createFieldBackedSetter(
                classWriter,
                generatedClass,
                property,
                lockable,
                setter
            )
        }
    }

    private fun createFieldBackedSetter(
        classWriter: ClassWriter,
        generatedClass: Type,
        property: ManagedProperty,
        lockable: Boolean,
        setter: Method
    ) {
        val type = property.supportedPropertyType
        check(type.implementationType == setter.argumentTypes[0]) {
            "Currently only setters that use the same type are supported."
            // TODO(b/140406102): Support setters for groovy +=
        }
        // Mark bridge methods as synthetic.
        val access = if(type.type == setter.argumentTypes[0]) property.access else property.access.or(Opcodes.ACC_SYNTHETIC)
        GeneratorAdapter(access, setter, null, null, classWriter).apply {
            loadThis()
            if (lockable) {
                // if (this.__locked__) { throw new AgpDslLockedExtension("...") }
                newLabel().also { actuallySet ->
                    getField(generatedClass, LOCK_FIELD_NAME, Type.BOOLEAN_TYPE)
                    visitJumpInsn(Opcodes.IFEQ, actuallySet)
                    // TODO: Share the base string between methods/classes?
                    // TODO: URL
                    throwException(
                        LOCKED_EXCEPTION,
                        "It is too late to set ${property.name}\n" +
                                "It has already been read to configure this project.\n" +
                                "Consider either moving this call to be during evaluation,\n" +
                                "or using the variant API."
                    )
                    visitLabel(actuallySet)
                }
            }
            // this.__managedField = argument;
            loadThis()
            loadArg(0)
            putField(generatedClass, property.backingFieldName, type.implementationType)
            returnValue()
            endMethod()
        }
    }

    private fun createFieldBackedGetters(
        classWriter: ClassWriter,
        generatedClass: Type,
        property: ManagedProperty,
    ) {
        for (getter in property.gettersToGenerate) {
            createFieldBackedGetter(classWriter, generatedClass, property, getter)
        }
    }
    private fun createFieldBackedGetter(
        classWriter: ClassWriter,
        generatedClass: Type,
        property: ManagedProperty,
        getter: Method,
    ) {
        val type = property.supportedPropertyType
        // Mark bridge methods as synthetic.
        val access = if(type.type == getter.returnType) property.access else property.access.or(Opcodes.ACC_SYNTHETIC)

        GeneratorAdapter(access, getter, null, null, classWriter).apply {
            loadThis()
            getField(generatedClass, property.backingFieldName, type.implementationType)
            checkCast(type.type)
            returnValue()
            endMethod()
        }
    }

    companion object {

        private const val LOCK_FIELD_NAME = "__locked__"

        private fun notAbstract(modifiers: Int): Int = modifiers and Modifier.ABSTRACT.inv()
        private val OBJECT_TYPE = Type.getType(Any::class.java)
        private val INJECT_TYPE = Type.getDescriptor(Inject::class.java)
        private val LOCKABLE_CONSTRUCTOR =
            Method("<init>", Type.VOID_TYPE, arrayOf(Type.getType(String::class.java)))
        private val LOCK_METHOD = Method("lock", Type.VOID_TYPE, arrayOf())
        private val LOCKED_EXCEPTION = Type.getType(AgpDslLockedException::class.java)

        // Use reflection to avoid needing to compile against java11 APIs yet.
        private val privateLookupInMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
            MethodHandles::class.java.getDeclaredMethod(
                "privateLookupIn",
                Class::class.java,
                MethodHandles.Lookup::class.java
            )
        }
        private val lookupDefineClassMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
            MethodHandles.Lookup::class.java.getDeclaredMethod("defineClass", ByteArray::class.java)
        }
    }

}
