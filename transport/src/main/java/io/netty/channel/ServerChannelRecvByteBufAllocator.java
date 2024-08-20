/*
 * Copyright 2021 The Netty Project
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
package io.netty.channel;

/**
 * 服务端 @{@link io.netty.channel.socket.ServerSocketChannel} 使用的 ByteBuf 分配器，专门用于服务端 ServerSocketChannel 接收客户端连接的场景
 * {@link MaxMessagesRecvByteBufAllocator} implementation which should be used for {@link ServerChannel}s.
 */
public final class ServerChannelRecvByteBufAllocator extends DefaultMaxMessagesRecvByteBufAllocator {
    public ServerChannelRecvByteBufAllocator() {
        super(1, true);
    }

    @Override
    public Handle newHandle() {
        return new MaxMessageHandle() {
            @Override
            public int guess() {
                return 128;
            }
        };
    }
}
