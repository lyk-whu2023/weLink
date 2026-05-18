package com.epsilon.welink.im.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSocketServer {

    @Value("${welink.websocket.port}")
    private int port;

    // 心跳超时时间，单位秒
    @Value("${welink.websocket.heartbeat-timeout}")
    private int heartbeatTimeout;

    // 事件循环组，用于处理客户端连接和消息
    private EventLoopGroup bossGroup;
    // 工作线程组，用于处理客户端消息
    private EventLoopGroup workerGroup;

    private final WebSocketChannelHandler webSocketChannelHandler;

    public WebSocketServer(WebSocketChannelHandler webSocketChannelHandler) {
        this.webSocketChannelHandler = webSocketChannelHandler;
    }

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        // 配置Netty服务器
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            /* 配置Netty通道管道
                            添加心跳超时处理
                            添加HTTP解码器
                            添加分块写入处理
                            添加HTTP对象聚合器
                            添加WebSocket协议处理
                            添加WebSocket通道处理 */
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IdleStateHandler(heartbeatTimeout, 0, 0, TimeUnit.SECONDS));
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new ChunkedWriteHandler());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                            pipeline.addLast(webSocketChannelHandler);
                        }
                    });

            //绑定端口并等待连接
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("WebSocket server started on port {}", port);
            //异步等待服务器关闭
            future.channel().closeFuture().addListener(f -> {
                log.info("WebSocket server closed");
            });
        } catch (Exception e) {
            log.error("WebSocket server start failed", e);
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("WebSocket server shutdown");
    }
}
