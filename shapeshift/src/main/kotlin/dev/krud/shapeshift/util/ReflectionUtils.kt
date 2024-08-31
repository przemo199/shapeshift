/*
 * Copyright KRUD 2022
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

@file:JvmName("ReflectionUtils")

package dev.krud.shapeshift.util

import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaField

data class ClassPair<From: Any, To: Any>(val from: KClass<out From>, val to: KClass<out To>)

internal fun Field.getValue(target: Any): Any? {
    return this.get(target)
}

internal fun Field.setValue(target: Any, value: Any?) {
    this.set(target, value)
}

internal fun Class<*>.getDeclaredFieldsRecursive(): List<Field> {
    var clazz: Class<*>? = this
    val fields = mutableListOf<Field>()
    while (clazz != null) {
        fields += clazz.declaredFields
        clazz = clazz.superclass
    }

    return fields
}

internal fun KClass<*>?.getDeclaredPropertyRecursive(name: String): KProperty1<*, *> {
    var clazz = this
    while (clazz != null) {
        val property = clazz.declaredMemberProperties.firstOrNull { it.name == name }
        if (property != null) {
            return property
        } else {
            clazz = clazz.superclasses.firstOrNull()
        }
    }
    throw NoSuchFieldException(name)
}

internal fun Field.getGenericAtPosition(position: Int): Class<*> {
    if (genericType !is ParameterizedType) {
        error("Type ${this.type} is not parameterized")
    }
    return (genericType as ParameterizedType).actualTypeArguments[position] as Class<*>
}

fun KProperty<*>.type(): Class<*> = ((this.returnType?.classifier as KClass<*>?) ?: (this.javaField?.type as KClass<*>)).java
