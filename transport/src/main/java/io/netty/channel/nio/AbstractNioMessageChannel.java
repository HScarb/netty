/*
 * Copyright 2012 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ServerChannel;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 {@link io.netty.channel.socket.nio.NioServerSocketChannel} 的基类，Message 指的是底层的客户端连接 {@link java.nio.channels.SocketChannel}
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {
    boolean inputShutdown;

    /**
     * @see AbstractNioChannel#AbstractNioChannel(Channel, SelectableChannel, int)
     */
    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (inputShutdown) {
            return;
        }
        super.doBeginRead();
    }

    protected boolean continueReading(RecvByteBufAllocator.Handle allocHandle) {
        return allocHandle.continueReading();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {

        /**
         * 存放连接建立后创建的客户端 {@link java.nio.channels.SocketChannel}
         */
        private final List<Object> readBuf = new ArrayList<Object>();

        @Override
        public void read() {
            // 必须在 Reactor 线程中运行
            assert eventLoop().inEventLoop();
            // 获取服务端 ServerSocketChannel 中的 config 和 pipeline
            final ChannelConfig config = config();
            final ChannelPipeline pipeline = pipeline();
            // 创建接收数据的 Buffer 的分配器（用于分配容量大小合适的 ByteBuf 来容纳接收的数据）
            // 在接收连接的场景中，这里的 allocHandle 用于控制 read loop 的循环读取创建连接的次数
            // 因为 Main Reactor 线程会被限制只能在 read loop 中向 NioServerSocketChannel 读取 16 次客户端连接，需要它记录读取次数
            final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
            allocHandle.reset(config);

            boolean closed = false;
            Throwable exception = null;
            try {
                try {
                    // read loop，循环读取
                    do {
                        // NioServerSocketChannel#doReadMessages，底层调用 NIO ServerSocketChannel#accept 创建客户端 SocketChannel，
                        // 并将创建的 SocketChannel 放入 readBuf。
                        // 返回 localRead 表示接收到多少客户端连接，正常情况下返回 1，<= 0 表示没有新的客户端连接可以接收
                        int localRead = doReadMessages(readBuf);
                        // 已经没有新的连接可接收（localRead <= 0），退出 read loop 循环
                        if (localRead == 0) {
                            break;
                        }
                        if (localRead < 0) {
                            closed = true;
                            break;
                        }

                        // 统计在当前事件循环中已经读取到的 Message 数量（接收连接的场景中为创建连接的个数）
                        allocHandle.incMessagesRead(localRead);
                    } while (continueReading(allocHandle)); // 判断是否需要结束 read loop（是否已经读满 16 次），结束 read 后 Reactor 可以执行异步任务
                } catch (Throwable t) {
                    exception = t;
                }

                // 遍历 readBuf 中的客户端 SocketChannel，传播 ChannelRead 事件
                int size = readBuf.size();
                for (int i = 0; i < size; i ++) {
                    readPending = false;
                    /**
                     * 在服务端 NioServerSocketChannel 对应的 pipeline 中传播 ChannelRead 事件，最终会传到 {@link io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor} 上。
                     * {@link io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor#channelRead(ChannelHandlerContext, Object)}
                     * 初始化客户端 {@link io.netty.channel.socket.SocketChannel} 并将其绑定到 Sub Reactor 组中的一个 Reactor 上
                     */
                    pipeline.fireChannelRead(readBuf.get(i));
                }
                // 清除本次 accept 创建的客户端 SocketChannel 集合
                readBuf.clear();
                allocHandle.readComplete();
                // 触发 readComplete 事件传播
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    closed = closeOnReadError(exception);

                    pipeline.fireExceptionCaught(exception);
                }

                if (closed) {
                    inputShutdown = true;
                    if (isOpen()) {
                        close(voidPromise());
                    }
                }
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!readPending && !config.isAutoRead()) {
                    removeReadOp();
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();

        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }
            try {
                boolean done = false;
                for (int i = config().getWriteSpinCount() - 1; i >= 0; i--) {
                    if (doWriteMessage(msg, in)) {
                        done = true;
                        break;
                    }
                }

                if (done) {
                    maxMessagesPerWrite--;
                    in.remove();
                } else {
                    break;
                }
            } catch (Exception e) {
                if (continueOnWriteError()) {
                    maxMessagesPerWrite--;
                    in.remove(e);
                } else {
                    throw e;
                }
            }
        }
        if (in.isEmpty()) {
            // Wrote all messages.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        } else {
            // Did not write all messages.
            if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                key.interestOps(interestOps | SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Returns {@code true} if we should continue the write loop on a write error.
     */
    protected boolean continueOnWriteError() {
        return false;
    }

    protected boolean closeOnReadError(Throwable cause) {
        if (!isActive()) {
            // If the channel is not active anymore for whatever reason we should not try to continue reading.
            return true;
        }
        if (cause instanceof PortUnreachableException) {
            return false;
        }
        if (cause instanceof IOException) {
            // ServerChannel should not be closed even on IOException because it can often continue
            // accepting incoming connections. (e.g. too many open files)
            return !(this instanceof ServerChannel);
        }
        return true;
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> buf) throws Exception;

    /**
     * Write a message to the underlying {@link java.nio.channels.Channel}.
     *
     * @return {@code true} if and only if the message has been written
     */
    protected abstract boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception;
}
