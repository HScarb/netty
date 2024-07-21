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
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.example.util.ServerUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx = ServerUtil.buildSslContext();

        // 创建主从 Reactor 线程组
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            /*
            netty有两种Channel类型：
            一种是服务端用于监听绑定端口地址的NioServerSocketChannel
            一种是用于客户端通信的NioSocketChannel。
            每种Channel类型实例都会对应一个PipeLine用于编排对应channel实例上的IO事件处理逻辑。
            PipeLine中组织的就是ChannelHandler用于编写特定的IO处理逻辑。

            ServerBootstrap启动类方法带有child前缀的均是设置客户端NioSocketChannel属性的。
             */
            // 配置主从 Reactor
            b.group(bossGroup, workerGroup)
                // 配置主 Reactor 中的服务端 channel 类型，NioServerSocketChannel 是 JDK NIO 中 ServerSocketChannel 的封装
             .channel(NioServerSocketChannel.class)
                // 设置服务端 ServerSocketChannel 的 SocketOption，SO_BACKLOG 表示服务端接受客户端连接的队列长度
             .option(ChannelOption.SO_BACKLOG, 100)
                // 设置服务端 ServerSocketChannel 中对应 Pipeline 中的 ChannelHandler
             .handler(new LoggingHandler(LogLevel.INFO))
                // 设置客户端 NioSocketChannel 中对应 Pipeline 中的 ChannelHandler
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 // ChannelInitializer 是当 SocketChannel 成功注册到绑定的 Reactor 上后，用于初始化该 SocketChannel 的 Pipeline
                 // initChannel 方法在注册成功后执行
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(serverHandler);
                 }
             });

            // 绑定端口地址，开始监听客户端连接事件 OP_ACCEPT
            // Start the server.
            ChannelFuture f = b.bind(PORT).sync();

            // 等待服务端 NioServerSocketChannel 关闭
            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // 优雅关闭主从 Reactor 线程组
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
