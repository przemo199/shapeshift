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

import dev.krud.shapeshift.transformer.EmptyTransformer
import dev.krud.shapeshift.transformer.base.MappingTransformer
import dev.krud.shapeshift.util.KClassPair
import kotlin.reflect.KClass

data class MappingTransformerRegistration<From : Any, To : Any>(
    val fromClazz: KClass<From>,
    val toClazz: KClass<To>,
    val transformer: MappingTransformer<From, To>,
    val default: Boolean = false
) {
    companion object {
        val EMPTY = MappingTransformerRegistration(
            Any::class,
            Any::class,
            EmptyTransformer,
            false
        )

        val <From : Any, To : Any> MappingTransformerRegistration<From, To>.id: KClassPair<From, To> get() = KClassPair(fromClazz, toClazz)

        inline fun <reified From : Any, reified To : Any> MappingTransformer<From, To>.toRegistration(default: Boolean = false): MappingTransformerRegistration<From, To> {
            return MappingTransformerRegistration(
                From::class,
                To::class,
                this,
                default
            )
        }
    }
}