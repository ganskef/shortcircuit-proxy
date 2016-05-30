package de.ganskef.shortcircuit.proxy.examples;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * This handler updates the connection with a ssl handler if needed and removes
 * himself from the pipeline. This handler must be the first in the pipeline
 * since the SslHandler needs to be the first. Additionally this handler must be
 * named with "sslupdate" to replace it with the SslHandler.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerSslUpdateHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(EvaluationServerSslUpdateHandler.class);

    private final SslContext sslCtx;

    public EvaluationServerSslUpdateHandler(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> outs) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        if (sslCtx != null && SslHandler.isEncrypted(buf)) {
            logger.info("Encrypted connection...");
            p.addAfter("sslupdate", "ssl", sslCtx.newHandler(ctx.alloc()));
        }
        p.remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

}
