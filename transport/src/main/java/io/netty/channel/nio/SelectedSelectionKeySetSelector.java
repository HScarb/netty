/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 * 封装类，将 unwrappedSelector 和与 sun.nio.ch.SelectorImpl 类关联好的 Netty 优化实现 SelectedSelectionKeySet 封装装饰起来。
 * 将所有对Selector的操作全部代理给unwrappedSelector，并在发起轮询IO事件的相关操作中，重置SelectedSelectionKeySet清空上一次的轮询结果。
 */
final class SelectedSelectionKeySetSelector extends Selector {
    /**
     * Netty 优化后的 SelectedKey 就绪集合
     */
    private final SelectedSelectionKeySet selectionKeys;
    /**
     * 优化后的 JDK NIO 原生 Selector
     */
    private final Selector delegate;

    SelectedSelectionKeySetSelector(Selector delegate, SelectedSelectionKeySet selectionKeys) {
        this.delegate = delegate;
        this.selectionKeys = selectionKeys;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public SelectorProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<SelectionKey> keys() {
        return delegate.keys();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return delegate.selectedKeys();
    }

    @Override
    public int selectNow() throws IOException {
        selectionKeys.reset();
        return delegate.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        // 重置 SelectedKeys 集合
        selectionKeys.reset();
        return delegate.select(timeout);
    }

    @Override
    public int select() throws IOException {
        // 重置 SelectedKeys 集合
        selectionKeys.reset();
        return delegate.select();
    }

    @Override
    public Selector wakeup() {
        return delegate.wakeup();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
