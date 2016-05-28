package de.ganskef.shortcircuit.proxy.examples;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

/**
 * This handler responds with a 404 Not Found response always to a completed
 * request. It's the last handler in the chain of the HTTP server.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerFallbackHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (isHandeled(msg)) {
                writeErrorResponse(ctx);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private boolean isHandeled(Object msg) {
        return msg instanceof LastHttpContent;
    }

    private void writeErrorResponse(ChannelHandlerContext ctx) {
        HttpResponseStatus status = HttpResponseStatus.NOT_FOUND;
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

}
