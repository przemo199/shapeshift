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

import dev.krud.shapeshift.decorator.EmptyDecorator
import dev.krud.shapeshift.decorator.MappingDecorator
import dev.krud.shapeshift.util.KClassPair
import kotlin.reflect.KClass

data class MappingDecoratorRegistration<From: Any, To: Any>(
    val fromClazz: KClass<From>,
    val toClazz: KClass<To>,
    val decorator: MappingDecorator<out Any, out Any>
) {
    companion object {
        val EMPTY = MappingDecoratorRegistration(
            Any::class,
            Any::class,
            EmptyDecorator
        )

        val <From : Any, To : Any> MappingDecoratorRegistration<From, To>.id: KClassPair<From, To> get() = KClassPair(fromClazz, toClazz)

        inline fun <reified From : Any, reified To : Any> MappingDecorator<From, To>.toRegistration(): MappingDecoratorRegistration<From, To> {
            return MappingDecoratorRegistration(From::class, To::class, this)
        }
    }
}
