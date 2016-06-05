package de.ganskef.shortcircuit.proxy.examples;

import de.ganskef.shortcircuit.proxy.SslContextFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * This handler responds to a CONNECT request and stores the address in the
 * channel context. Other requests will be chained to the next handler in the
 * pipeline.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerConnectHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(EvaluationServerConnectHandler.class);

    private final SslContextFactory sslCtxFactory;

    private final AttributeKey<String> connectedAttributeKey;

    public EvaluationServerConnectHandler(SslContextFactory sslCtxFactory, AttributeKey<String> connectedAttributeKey) {
        this.sslCtxFactory = sslCtxFactory;
        this.connectedAttributeKey = connectedAttributeKey;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest && isHandeled((HttpRequest) msg)) {
            ctx.pipeline().channel().attr(connectedAttributeKey).set(((HttpRequest) msg).uri());
            ctx.writeAndFlush(connectedResponse());
            ctx.pipeline().addFirst(new EvaluationServerSslUpdateHandler(sslCtxFactory, connectedAttributeKey));
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    protected boolean isHandeled(HttpRequest request) {
        return request.method() == HttpMethod.CONNECT;
    }

    private HttpResponse connectedResponse() {
        HttpResponseStatus status = new HttpResponseStatus(200, "Connection established");
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().set("Proxy-Connection", HttpHeaderValues.KEEP_ALIVE);
        // TODO add via header
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("An exception was thrown:", cause);
        ctx.close();
    }

}
