/*
 * Copyright KRUD 2022
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.krud.shapeshift.resolver.annotation

import dev.krud.shapeshift.dto.ResolvedMappedProperty
import dev.krud.shapeshift.dto.TransformerCoordinates
import dev.krud.shapeshift.resolver.MappingDefinition
import dev.krud.shapeshift.resolver.MappingDefinitionResolver
import dev.krud.shapeshift.util.getAutoMappings
import dev.krud.shapeshift.util.getDeclaredPropertyRecursive
import dev.krud.shapeshift.util.splitIgnoreEmpty
import dev.krud.shapeshift.util.type
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaField

class AnnotationMappingDefinitionResolver : MappingDefinitionResolver {
    override fun resolve(fromClazz: KClass<*>, toClazz: KClass<*>): MappingDefinition {
        val mappedFieldReferences = getMappedFields(fromClazz, toClazz)

        val resolvedMappedProperties = mutableListOf<ResolvedMappedProperty>()
        for ((mappedField, field) in mappedFieldReferences) {
            val transformerCoordinates = TransformerCoordinates.ofType(mappedField.transformer.java)

            val mapFromCoordinates = resolveNodesToFields(mappedField.mapFrom.splitIgnoreEmpty(NODE_DELIMITER), field, fromClazz)

            val effectiveMapTo = mappedField.mapTo.ifBlank {
                mapFromCoordinates.last().name
            }

            val mapToCoordinates = resolveNodesToFields(effectiveMapTo.splitIgnoreEmpty(NODE_DELIMITER), null, toClazz)
            val conditionClazz = if (mappedField.condition != Nothing::class) {
                mappedField.condition
            } else {
                null
            }

            resolvedMappedProperties += ResolvedMappedProperty(
                mapFromCoordinates,
                mapToCoordinates,
                transformerCoordinates,
                null,
                conditionClazz?.java,
                null,
                mappedField.overrideMappingStrategy
            )
        }
        resolvedMappedProperties += generateAutoMappings(fromClazz, toClazz).filter { autoResolvedMappedField ->
            resolvedMappedProperties.none {
                it.mapFromProperties.first() == autoResolvedMappedField.mapFromProperties.first() || it.mapToProperties.first() == autoResolvedMappedField.mapToProperties.first()
            }
        }
        return MappingDefinition(fromClazz, toClazz, resolvedMappedProperties)
    }

    private fun generateAutoMappings(fromClazz: KClass<*>, toClazz: KClass<*>): List<ResolvedMappedProperty> {
        val autoMappingAnnotations = fromClazz.annotations.filter { it is AutoMapping } as List<AutoMapping>

        if (autoMappingAnnotations.isEmpty()) {
            return emptyList()
        }

        val effectiveAnnotation = autoMappingAnnotations.firstOrNull { it.target == toClazz }
            ?: (autoMappingAnnotations.firstOrNull { it.target == Nothing::class } ?: return emptyList())
        return getAutoMappings(fromClazz, toClazz, effectiveAnnotation.strategy)
    }

    /**
     * Get true from field from path like "user.address.city" delimtied by [NODE_DELIMITER]
     */
    fun resolveNodesToFields(nodes: List<String>, field: KProperty1<*, *>?, clazz: KClass<*>): List<KProperty1<*, *>> {
        if (field == null) {
            if (nodes.isEmpty()) {
                error("Unable to determine mapped field for $clazz")
            }
            val realField = clazz.getDeclaredPropertyRecursive(nodes.first())
            return resolveNodesToFields(nodes.drop(1), realField, realField.type().kotlin)
        }

        if (nodes.isEmpty()) {
            return listOf(field)
        }
        val nextField = field.type().kotlin.getDeclaredPropertyRecursive(nodes.first())
        return listOf(field) + resolveNodesToFields(nodes.drop(1), nextField, nextField.type().kotlin)
    }

    private fun getMappedFields(fromClass: KClass<*>, toClass: KClass<*>): List<MappedFieldReference> {
        var clazz: KClass<*>? = fromClass
        val result = mutableListOf<MappedFieldReference>()
        while (clazz != null) {
            val defaultMappingTarget = fromClass.annotations.firstOrNull { it is DefaultMappingTarget } as DefaultMappingTarget?
            val defaultToClass = defaultMappingTarget?.value ?: Nothing::class
            val properties = fromClass.memberProperties
            result += clazz.annotations
                .filter { mappedField ->
                    if (mappedField !is MappedField) return@filter false
                    try {
                        return@filter isOfType(defaultToClass, (mappedField).target, toClass)
                    } catch (e: IllegalStateException) {
                        error("Could not create entity structure for <" + fromClass.simpleName + ", " + toClass.simpleName + ">: " + e.message)
                    }
                }
                .map { MappedFieldReference(it as MappedField) }
            for (property in properties) {
                val annotations = property.annotations.toMutableList()
                property.javaField?.annotations?.let {
                    annotations.addAll(it)
                }
                result += annotations
                    .filter { mappedField ->
                        if (mappedField !is MappedField) return@filter false
                        try {
                            return@filter isOfType(defaultToClass, mappedField.target, toClass)
                        } catch (e: IllegalStateException) {
                            throw IllegalStateException("Could not create entity structure for <" + fromClass.simpleName + ", " + toClass.simpleName + ">: " + e.message)
                        }
                    }
                    .map { MappedFieldReference(it as MappedField, property) }
            }
            clazz = clazz.superclasses.firstOrNull()
        }
        return result
    }

    private fun isOfType(defaultToClass: KClass<*>, fromClass: KClass<*>, toClass: KClass<*>): Boolean {
        var trueFromClass = fromClass
        if (trueFromClass == Nothing::class) {
            check(defaultToClass != Nothing::class) { "No mapping target or default mapping target specified" }
            trueFromClass = defaultToClass
        }
        return toClass.isSubclassOf(trueFromClass)
    }

    private data class MappedFieldReference(
        val mappedField: MappedField,
        val field: KProperty1<*, *>? = null
    )

    companion object {
        private const val NODE_DELIMITER = "."
    }
}
