package de.ganskef.shortcircuit.proxy.examples;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

/**
 * This handler responds to a request to the / URI only. Other requests will be
 * chained to the next handler in the pipeline.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerHomeHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest && isHandeled((FullHttpRequest) msg)) {
            try {
                writeOkResponse(ctx);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }

    protected boolean isHandeled(FullHttpRequest request) {
        String uri = request.uri();
        return uri.equals("/");
    }

    private void writeOkResponse(ChannelHandlerContext ctx) {
        HttpResponseStatus status = HttpResponseStatus.OK;
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Response status: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

}
