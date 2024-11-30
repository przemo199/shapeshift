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
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.superclasses

data class KClassPair<From: Any, To: Any>(val from: KClass<out From>, val to: KClass<out To>)

internal fun Field.getValue(target: Any): Any? {
    return get(target)
}

internal fun Field.setValue(target: Any, value: Any?) {
    set(target, value)
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

internal fun KProperty<*>.findGenericAtPosition(position: Int): KClass<*>? {
    if (returnType.arguments.size <= position) {
        return null
    }
    return returnType.arguments[position].type?.classifier as KClass<*>
}

internal fun KProperty<*>.getGenericAtPosition(position: Int): KClass<*> {
    findGenericAtPosition(position)?.let {
        return it
    }
    error("Type $returnType is not parameterized")
}

fun KProperty<*>.type(): KClass<*> = returnType.classifier as KClass<*>
