/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.provider;

import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

class BiProvider<R, A, B> extends AbstractMinimalProvider<R> {

    private final BiFunction<? super A, ? super B, ? extends R> combiner;
    private final ProviderInternal<A> left;
    private final ProviderInternal<B> right;

    public BiProvider(Provider<A> left, Provider<B> right, BiFunction<? super A, ? super B, ? extends R> combiner) {
        this.combiner = combiner;
        this.left = Providers.internal(left);
        this.right = Providers.internal(right);
    }

    @Override
    public String toString() {
        return String.format("and(%s, %s)", left, right);
    }

    @Override
    public ExecutionTimeValue<? extends R> calculateExecutionTimeValue() {
        return isChangingValue(left) || isChangingValue(right)
            ? ExecutionTimeValue.changingValue(this)
            : super.calculateExecutionTimeValue();
    }

    private boolean isChangingValue(ProviderInternal<?> provider) {
        return provider.calculateExecutionTimeValue().isChangingValue();
    }

    @Override
    protected Value<? extends R> calculateOwnValue(ValueConsumer consumer) {
        Value<? extends A> leftValue = left.calculateValue(consumer);
        if (leftValue.isMissing()) {
            return leftValue.asType();
        }
        Value<? extends B> rightValue = right.calculateValue(consumer);
        if (rightValue.isMissing()) {
            return rightValue.asType();
        }

        A leftUnpackedValue = leftValue.getWithoutSideEffect();
        B rightUnpackedValue = rightValue.getWithoutSideEffect();
        R combinedUnpackedValue = combiner.apply(leftUnpackedValue, rightUnpackedValue);

        SideEffect<?> leftSideEffect = leftValue.getSideEffect();
        SideEffect<R> fixedLeftSideEffect = leftSideEffect == null ? null : SideEffect.fixed(leftUnpackedValue, Cast.uncheckedNonnullCast(leftSideEffect));
        SideEffect<?> rightSideEffect = rightValue.getSideEffect();
        SideEffect<R> fixedRightSideEffect = rightSideEffect == null ? null : SideEffect.fixed(rightUnpackedValue, Cast.uncheckedNonnullCast(rightSideEffect));

        Value<R> result = Value.of(combinedUnpackedValue);
        if (leftSideEffect != null && rightSideEffect != null) {
            result = result.withSideEffect(SideEffect.composite(fixedLeftSideEffect, fixedRightSideEffect));
        } else if (leftSideEffect != null) {
            result = result.withSideEffect(fixedLeftSideEffect);
        } else if (rightSideEffect != null) {
            result = result.withSideEffect(fixedRightSideEffect);
        }
        return result;
    }

    @Nullable
    @Override
    public Class<R> getType() {
        // Could do a better job of inferring this
        return null;
    }

    @Override
    public ValueProducer getProducer() {
        return new PlusProducer(left.getProducer(), right.getProducer());
    }
}
