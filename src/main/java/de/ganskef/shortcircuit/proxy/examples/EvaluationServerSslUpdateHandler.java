package de.ganskef.shortcircuit.proxy.examples;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;

import de.ganskef.shortcircuit.proxy.SslContextFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * This handler updates the connection with a ssl handler if needed and removes
 * himself from the pipeline. This handler must be the first in the pipeline
 * since the SslHandler needs to be the first.
 * 
 * It should be possible the other way, provide a SslHandler generally, and
 * remove it if not needed.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerSslUpdateHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(EvaluationServerSslUpdateHandler.class);

    private final SslContextFactory sslCtxFactory;

    private final AttributeKey<String> connectedAttributeKey;

    public EvaluationServerSslUpdateHandler(SslContextFactory sslCtxFactory,
            AttributeKey<String> connectedAttributeKey) {
        this.sslCtxFactory = sslCtxFactory;
        this.connectedAttributeKey = connectedAttributeKey;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> outs) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        if (sslCtxFactory != null && SslHandler.isEncrypted(buf)) {
            String cn = findCertificateName(ctx.channel());
            logger.info("Detect encrypted connection to {}...", cn);
            SslContext sslCtx = sslCtxFactory.getSslContext(cn);
            String name = findHandlerName(pipeline);
            pipeline.addAfter(name, null, sslCtx.newHandler(ctx.alloc()));
        }
        pipeline.remove(this);
    }

    private String findCertificateName(Channel channel) {
        if (channel.hasAttr(connectedAttributeKey)) {
            String connected = channel.attr(connectedAttributeKey).get();
            int endIndex = connected.indexOf(":");
            if (endIndex != -1) {
                return connected.substring(0, endIndex);
            } else {
                return connected;
            }
        }
        try {
            return InetAddress.getByName("localhost").getHostName();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private String findHandlerName(ChannelPipeline pipeline) {
        for (Entry<String, ChannelHandler> each : pipeline) {
            if (each.getValue().getClass() == EvaluationServerSslUpdateHandler.class) {
                return each.getKey();
            }
        }
        throw new IllegalStateException("Not in pipeline " + pipeline);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("An exception was thrown:", cause);
        ctx.close();
    }

}
