/*
 * Copyright KRUD 2022
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.krud.shapeshift.util

import dev.krud.shapeshift.dto.ResolvedMappedProperty
import dev.krud.shapeshift.dto.TransformerCoordinates
import dev.krud.shapeshift.enums.AutoMappingStrategy
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

internal fun <From: Any, To: Any> getAutoMappings(fromClazz: KClass<From>, toClazz: KClass<To>, strategy: AutoMappingStrategy): List<ResolvedMappedProperty> {
    if (AutoMappingStrategy.NONE == strategy) {
        return mutableListOf<ResolvedMappedProperty>()
    }

    val resolvedMappedProperties = mutableListOf<ResolvedMappedProperty>()
    val fromProperties = fromClazz.memberProperties
    val toProperties = toClazz.memberProperties
    for (fromProperty in fromProperties) {
        val toProperty = toProperties.find {
            when (strategy) {
                AutoMappingStrategy.BY_NAME -> it.name == fromProperty.name
                AutoMappingStrategy.BY_NAME_AND_TYPE -> it.name == fromProperty.name && it.type() == fromProperty.type()
                else -> error("Unsupported auto mapping strategy")
            }
        } ?: continue
        resolvedMappedProperties += ResolvedMappedProperty(
            listOf(fromProperty),
            listOf(toProperty),
            TransformerCoordinates.NONE,
            null,
            null,
            null,
            null
        )
    }
    return resolvedMappedProperties
}
