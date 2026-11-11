package com.agri_system.infrastructure.common.http;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** 按实际流字节数限内存，取消也作用于迟到的订阅；不持有URL、凭据或正文日志。 */
public final class BoundedResponseBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int maximum;
    private final Supplier<? extends RuntimeException> overflow,network;
    private final CompletableFuture<byte[]> result=new CompletableFuture<>();
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    public BoundedResponseBody(int maximum,Supplier<? extends RuntimeException> overflow,Supplier<? extends RuntimeException> network) {
        if(maximum<1||overflow==null||network==null)throw new IllegalArgumentException("响应上限无效");
        this.maximum=maximum;this.overflow=overflow;this.network=network;
    }
    @Override public CompletionStage<byte[]> getBody(){return result;}
    @Override public void onSubscribe(Flow.Subscription incoming) {
        if(!subscription.compareAndSet(null,incoming)||cancelled.get()){incoming.cancel();return;}
        incoming.request(Long.MAX_VALUE);
    }
    @Override public void onNext(List<ByteBuffer> parts) {
        if(result.isDone())return;
        for(var part:parts) {
            if(part.remaining()>maximum-bytes.size()){result.completeExceptionally(overflow.get());cancel();return;}
            byte[] chunk=new byte[part.remaining()];part.get(chunk);bytes.writeBytes(chunk);
        }
    }
    @Override public void onError(Throwable ignored){result.completeExceptionally(network.get());}
    @Override public void onComplete(){result.complete(bytes.toByteArray());}
    public void cancel() {
        cancelled.set(true);var current=subscription.get();if(current!=null)current.cancel();
        result.completeExceptionally(network.get());
    }
}
