/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactor 选择器工厂，负责生成选择策略，选择 Channel 与哪个 Reactor 绑定
 * 默认策略采用 Round-Robin 轮询选择下一个 Reactor
 * Default implementation which uses simple round-robin to choose next {@link EventExecutor}.
 */
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        // 判断 Reactor 线程组中的 Reactor 个数是否为 2 的幂次方
        if (isPowerOfTwo(executors.length)) {
            // 使用位运算 & 代替取模运算 %，提高效率
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            // 使用普通的取模运算 %
            return new GenericEventExecutorChooser(executors);
        }
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 轮询选择下一个 Reactor，使用 & 运算符
         * 如果 executors.length 是 2的幂次方，那么 idx & (executors.length - 1) == idx % executors.length
         *
         * 如果 executors.length 是 2 的次幂（例如 2, 4, 8, 16 等），那么 executors.length 的二进制表示形式是 100...0，即只有一个 1，后面跟随若干个 0
         * executors.length - 1 的二进制表示形式是 011...1，即所有位都是 1
         * & 运算符是按位与运算，它会将两个操作数的每一位进行与操作。idx & (executors.length - 1) 的结果是 idx 的低位部分
         */
        @Override
        public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        // Use a 'long' counter to avoid non-round-robin behaviour at the 32-bit overflow boundary.
        // The 64-bit long solves this by placing the overflow so far into the future, that no system
        // will encounter this in practice.
        private final AtomicLong idx = new AtomicLong();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 轮询选择下一个 Reactor，使用 % 运算符
         */
        @Override
        public EventExecutor next() {
            return executors[(int) Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
