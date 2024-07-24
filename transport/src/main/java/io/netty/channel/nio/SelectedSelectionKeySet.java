/*
 * Copyright 2013 The Netty Project
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

import java.nio.channels.SelectionKey;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 对 JDK NIO 中 HashSet 保存的 SelectionKey 的优化，采用数组替换到JDK中的HashSet,这样add操作和遍历操作效率更高，不需要考虑hash冲突
 */
final class SelectedSelectionKeySet extends AbstractSet<SelectionKey> {

    /**
     * 采用数组替换到JDK中的HashSet,这样add操作和遍历操作效率更高，不需要考虑hash冲突
     */
    SelectionKey[] keys;
    /**
     * 数组尾部指针
     */
    int size;

    SelectedSelectionKeySet() {
        // 初始化数组大小为 1024
        keys = new SelectionKey[1024];
    }

    /**
     * 数组的添加效率高于 HashSet 因为不需要考虑hash冲突
     */
    @Override
    public boolean add(SelectionKey o) {
        if (o == null) {
            return false;
        }

        // 容量不够时扩容数组，到原来的两倍
        if (size == keys.length) {
            increaseCapacity();
        }

        // 时间复杂度 O(1)
        keys[size++] = o;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        SelectionKey[] array = keys;
        for (int i = 0, s = size; i < s; i++) {
            SelectionKey k = array[i];
            if (k.equals(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * 采用数组的遍历效率 高于 HashSet
     */
    @Override
    public Iterator<SelectionKey> iterator() {
        return new Iterator<SelectionKey>() {
            private int idx;

            @Override
            public boolean hasNext() {
                return idx < size;
            }

            @Override
            public SelectionKey next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return keys[idx++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    void reset() {
        reset(0);
    }

    void reset(int start) {
        Arrays.fill(keys, start, size, null);
        size = 0;
    }

    /**
     * 扩容数组，容量翻倍
     */
    private void increaseCapacity() {
        SelectionKey[] newKeys = new SelectionKey[keys.length << 1];
        System.arraycopy(keys, 0, newKeys, 0, size);
        keys = newKeys;
    }
}
