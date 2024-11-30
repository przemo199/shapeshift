/*
 * Copyright KRUD 2022
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.krud.shapeshift

import dev.krud.shapeshift.MappingDecoratorRegistration.Companion.id
import dev.krud.shapeshift.MappingTransformerRegistration.Companion.id
import dev.krud.shapeshift.condition.MappingCondition
import dev.krud.shapeshift.condition.MappingConditionContext
import dev.krud.shapeshift.container.ContainerAdapter
import dev.krud.shapeshift.decorator.MappingDecorator
import dev.krud.shapeshift.decorator.MappingDecoratorContext
import dev.krud.shapeshift.dto.MappingStructure
import dev.krud.shapeshift.dto.ObjectPropertyTrio
import dev.krud.shapeshift.dto.ResolvedMappedProperty
import dev.krud.shapeshift.dto.TransformerCoordinates
import dev.krud.shapeshift.resolver.MappingDefinitionResolver
import dev.krud.shapeshift.transformer.base.MappingTransformer
import dev.krud.shapeshift.transformer.base.MappingTransformerContext
import dev.krud.shapeshift.util.KClassPair
import dev.krud.shapeshift.util.concurrentMapOf
import dev.krud.shapeshift.util.setValue
import dev.krud.shapeshift.util.type
import java.util.function.Supplier
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

class ShapeShift internal constructor(
    transformersRegistrations: Set<MappingTransformerRegistration<out Any, out Any>>,
    val mappingDefinitionResolvers: Set<MappingDefinitionResolver>,
    val defaultMappingStrategy: MappingStrategy,
    val decoratorRegistrations: Set<MappingDecoratorRegistration<out Any, out Any>>,
    val objectSuppliers: Map<KClass<*>, Supplier<*>>,
    val containerAdapters: Map<KClass<*>, ContainerAdapter<out Any>>
) {
    val transformerRegistrations: MutableList<MappingTransformerRegistration<out Any, out Any>> = mutableListOf()
    internal val transformersByTypeCache: MutableMap<KClass<out MappingTransformer<out Any?, out Any?>>, MappingTransformerRegistration<out Any, out Any>> =
        concurrentMapOf()
    internal val defaultTransformers: MutableMap<KClassPair<out Any, out Any>, MappingTransformerRegistration<out Any, out Any>> = mutableMapOf()
    private val mappingStructures: MutableMap<KClassPair<out Any, out Any>, MappingStructure> = concurrentMapOf()
    private val conditionCache: MutableMap<KClass<out MappingCondition<*>>, MappingCondition<*>> = concurrentMapOf()
    private val decoratorCache: MutableMap<KClassPair<out Any, out Any>, List<MappingDecorator<*, *>>> = concurrentMapOf()

    init {
        if (defaultMappingStrategy == MappingStrategy.NONE) {
            error("Default mapping strategy cannot be NONE")
        }
        for (registration in transformersRegistrations) {
            registerTransformer(registration)
        }
    }

    inline fun <From : Any, reified To : Any> map(fromObject: From): To {
        return map(fromObject, To::class)
    }

    fun <From : Any, To : Any> map(fromObject: From, toClazz: Class<To>): To {
        return map(fromObject, toClazz.kotlin)
    }

    /**
     * Map between the [fromObject] and a new instance of [toClazz]
     * [toClazz] MUST have a no-arg constructor when using this override
     */
    fun <From : Any, To : Any> map(fromObject: From, toClazz: KClass<To>): To {
        val toObject = initializeObject(toClazz)
        return map(fromObject, toObject)
    }

    /**
     * Map between the [fromObject] and [toObject] objects
     */
    fun <From : Any, To : Any> map(fromObject: From, toObject: To): To {
        val toClazz = toObject::class
        val mappingStructure = getMappingStructure(fromObject::class, toClazz)

        for (resolvedMappedProperty in mappingStructure.resolvedMappedProperties) {
            mapProperty(fromObject, toObject, resolvedMappedProperty)
        }

        val KClassPair = KClassPair(fromObject::class, toClazz)
        val decorators = getDecorators<From, To>(KClassPair)
        if (decorators.isNotEmpty()) {
            val context = MappingDecoratorContext(fromObject, toObject, this)
            for (decorator in decorators) {
                decorator.decorate(context)
            }
        }

        return toObject
    }

    /**
     * Map [fromObjects] to a list of [toClazz] objects
     */
    fun <From : Any, To : Any> mapCollection(fromObjects: Collection<From>, toClazz: KClass<To>): List<To> {
        val toObjects = mutableListOf<To>()
        for (fromObject in fromObjects) {
            toObjects.add(map(fromObject, toClazz))
        }
        return toObjects
    }

    /**
     * Map [fromObjects] to a list of [toClazz] objects
     */
    inline fun <From : Any, reified To : Any> mapCollection(fromObjects: Collection<From>): List<To> {
        return mapCollection(fromObjects, To::class)
    }

    private fun <From : Any, To : Any> mapProperty(fromObject: From, toObject: To, resolvedMappedProperty: ResolvedMappedProperty) {
        val fromPair = getFromPairInstanceByNodes(resolvedMappedProperty.mapFromProperties, fromObject) ?: return
        val toPair = getToPairInstanceByNodes(resolvedMappedProperty.mapToProperties, toObject) ?: return
        val transformerRegistration = getTransformer(resolvedMappedProperty.transformerCoordinates, fromPair, toPair)
        fromPair.property.isAccessible = true
        toPair.property.isAccessible = true

        val mappingStrategy = resolvedMappedProperty.effectiveMappingStrategy(defaultMappingStrategy)
        var fromValue = fromPair.property.getter.call(fromPair.target)
        val shouldMapValue = when (mappingStrategy) {
            MappingStrategy.NONE -> error("Mapping strategy is set to NONE")
            MappingStrategy.MAP_ALL -> true
            MappingStrategy.MAP_NOT_NULL -> fromValue != null
        }

        if (shouldMapValue) {
            fromValue = fromPair.property.getEffectiveValue(fromPair.target)
            try {
                if (!resolvedMappedProperty.conditionMatches(fromValue)) {
                    return
                }

                val valueToSet = if (resolvedMappedProperty.transformer != null) {
                    val context = MappingTransformerContext(fromValue, fromObject, toObject, fromPair.property, toPair.property, this)
                    (resolvedMappedProperty.transformer as MappingTransformer<Any, Any>).transform(context)
                } else if (transformerRegistration != MappingTransformerRegistration.EMPTY) {
                    val context = MappingTransformerContext(fromValue, fromObject, toObject, fromPair.property, toPair.property, this)
                    (transformerRegistration.transformer as MappingTransformer<Any, Any>).transform(context)
                } else {
                    fromValue
                }

                if (valueToSet == null) {
                    toPair.property.setEffectiveValue(toPair.target, null)
                } else {
                    if (!toPair.type.isSubclassOf(valueToSet::class)) {
                        error("Type mismatch: Expected ${toPair.type} but got ${valueToSet::class}")
                    }
                    toPair.property.setEffectiveValue(toPair.target, valueToSet)
                }
            } catch (e: Exception) {
                val newException =
                    IllegalStateException("Could not map value ${fromPair.property.name} of class ${fromPair.target.javaClass.simpleName} to ${toPair.property.name} of class ${toPair.target.javaClass.simpleName}: ${e.message}")
                newException.initCause(e)
                throw newException
            }
        }
    }

    private fun ResolvedMappedProperty.conditionMatches(value: Any?): Boolean {
        val condition = condition
            ?: conditionClazz?.getCachedInstance()
            ?: return true
        val context = MappingConditionContext(value, this@ShapeShift)
        return (condition as MappingCondition<Any>).isValid(context)
    }

    private fun ResolvedMappedProperty.effectiveMappingStrategy(defaultMappingStrategy: MappingStrategy): MappingStrategy {
        return if (overrideMappingStrategy != null && overrideMappingStrategy != MappingStrategy.NONE) {
            overrideMappingStrategy
        } else {
            defaultMappingStrategy
        }
    }

    private fun KClass<out MappingCondition<*>>?.getCachedInstance(): MappingCondition<*>? {
        this ?: return null
        return conditionCache.computeIfAbsent(this) {
            this.constructors.find { it.parameters.isEmpty() }!!.call()
        }
    }

    private fun getFromPairInstanceByNodes(nodes: List<KProperty1<*, *>>, target: Any?): ObjectPropertyTrio? {
        // This if only applies to recursive runs of this function
        // When target is null and type is from, don't attempt to instantiate the object
        if (target == null) {
            return null
        }

        val property = nodes.first()

        if (nodes.size == 1) {
            return ObjectPropertyTrio(target, property, property.getTrueType())
        }

        property.isAccessible = true
        var subTarget = property.getter.call(target)

        return getFromPairInstanceByNodes(nodes.drop(1), subTarget)
    }

    private fun getToPairInstanceByNodes(nodes: List<KProperty1<*, *>>, target: Any?): ObjectPropertyTrio? {
        // This if only applies to recursive runs of this function
        // When target is null and type is from, don't attempt to instantiate the object
        if (target == null) {
            error("$nodes leads to a null target")
        }

        val property = nodes.first()

        if (nodes.size == 1) {
            return ObjectPropertyTrio(target, property, property.getTrueType())
        }
        property.isAccessible = true
        var subTarget = property.getter.call(target)

        if (subTarget == null) {
            subTarget = initializeObject(property.type())
            property.setEffectiveValue(target, subTarget)
        }

        return getToPairInstanceByNodes(nodes.drop(1), subTarget)
    }

    private fun getTransformerByType(type: KClass<out MappingTransformer<out Any?, out Any?>>): MappingTransformerRegistration<out Any, out Any> {
        return transformersByTypeCache.computeIfAbsent(type) {
            transformerRegistrations.find { it.transformer::class == type } ?: MappingTransformerRegistration.EMPTY
        }
    }

    private fun getMappingStructure(fromClass: KClass<*>, toClass: KClass<*>): MappingStructure {
        val key = KClassPair(fromClass, toClass)
        return mappingStructures.computeIfAbsent(key) {
            val resolutions = mappingDefinitionResolvers
                .mapNotNull { it.resolve(fromClass, toClass) }

            MappingStructure(fromClass, toClass, resolutions.flatMap { it.resolvedMappedProperties })
        }
    }

    private fun <From : Any, To : Any> getDecorators(KClassPair: KClassPair<From, To>): List<MappingDecorator<From, To>> {
        return decoratorCache.computeIfAbsent(KClassPair) {
            decoratorRegistrations
                .filter { decoratorRegistration ->
                    decoratorRegistration.id == KClassPair
                }
                .map { decoratorRegistrations ->
                    decoratorRegistrations.decorator
                }
        } as List<MappingDecorator<From, To>>
    }

    private fun getTransformer(
        coordinates: TransformerCoordinates,
        fromPair: ObjectPropertyTrio,
        toPair: ObjectPropertyTrio
    ): MappingTransformerRegistration<*, *> {
        if (coordinates.type == null) {
            val key = KClassPair(fromPair.type, toPair.type)
            defaultTransformers[key]?.let {
                return it
            }
            return MappingTransformerRegistration.EMPTY
        }

        getTransformerByType(coordinates.type).let {
            if (MappingTransformerRegistration.EMPTY == it) {
                error("Could not find transformer by type [ ${coordinates.type} ] on $fromPair")
            }
            return it
        }
    }

    private fun <From : Any, To : Any> registerTransformer(registration: MappingTransformerRegistration<From, To>) {
        if (registration.default) {
            val existingDefaultTransformer = defaultTransformers[registration.id]
            if (existingDefaultTransformer != null) {
                error("Default transformer with pair ${registration.id} already exists")
            }
            defaultTransformers[registration.id] = registration
        }

        transformerRegistrations.add(registration)
        transformersByTypeCache.remove(registration.transformer::class)
    }

    private fun <Type : Any> initializeObject(clazz: KClass<Type>): Type {
        val supplier = objectSuppliers[clazz]
        if (supplier != null) {
            return supplier.get() as Type
        }
        val constructor = clazz.java.constructors.find { it.parameters.isEmpty() }
        if (constructor != null) {
            return constructor.newInstance() as Type
        }
        error("Could not find a no-arg constructor or object supplier for class $clazz")
    }

    private val KProperty1<*, *>.isContainer: Boolean get() = type() in containerAdapters

    private fun KProperty1<*, *>.getEffectiveValue(target: Any): Any? {
        val value = getter.call(target)
        if (isContainer) {
            return (containerAdapters[type()] as ContainerAdapter<Any>).unwrapValue(value)
        }
        return value
    }

    private fun KProperty1<*, *>.setEffectiveValue(target: Any, value: Any?) {
        val effectiveValue = if (isContainer) {
            containerAdapters[type()]?.wrapValue(value)
        } else {
            value
        }

        if (this is KMutableProperty1) {
            setter.call(target, effectiveValue)
        } else {
            javaField?.setValue(target, effectiveValue)
        }
    }

    private fun KProperty1<*, *>.getTrueType(): KClass<*> {
        return if (isContainer) {
            (containerAdapters[type()] as ContainerAdapter<*>).getTrueType(this)
        } else {
            type()
        }
    }
}
