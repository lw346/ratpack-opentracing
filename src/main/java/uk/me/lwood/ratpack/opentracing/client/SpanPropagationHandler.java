package uk.me.lwood.ratpack.opentracing.client;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;
import ratpack.registry.Registry;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Replaces the {@link HttpClient} with a per-request instance that will initialise a client side span on the
 * {@link Tracer} as a child of the current active Span, and forward the Span details as HTTP headers over the wire
 * to the remote downstream.
 *
 * {@link ClientSpanDecorator}s are used to decorate the resulting client span, and {@link ClientOperationNameProvider}
 * is used to determine the name of the operation that this span represents.
 *
 * This handler should be inserted at the start of the chain using {@link ratpack.handling.Chain#all(Handler)}, after
 * the {@link uk.me.lwood.ratpack.opentracing.server.SpanInitHandler} (such that there is a Span available in the
 * Context registry).
 */
public class SpanPropagationHandler implements Handler {
    static final String COMPONENT_NAME = "ratpack-client";

    private final Tracer tracer;
    private final List<ClientSpanDecorator> clientSpanDecorators;
    private final ClientOperationNameProvider operationNameProvider;

    public SpanPropagationHandler(Tracer tracer, List<ClientSpanDecorator> clientSpanDecorators, ClientOperationNameProvider operationNameProvider) {
        this.tracer = tracer;
        this.clientSpanDecorators = clientSpanDecorators;
        this.operationNameProvider = operationNameProvider;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        Span parentSpan = ctx.get(Span.class);

        HttpClient client = ctx.get(HttpClient.class).copyWith(spec -> {
            final AtomicReference<Span> spanReference = new AtomicReference<>();

            spec.requestIntercept(requestSpec -> {
                Span span = tracer.buildSpan(operationNameProvider.provideOperationName(requestSpec))
                        .ignoreActiveSpan()
                        .asChildOf(parentSpan)
                        .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .start();

                clientSpanDecorators.forEach(decorator -> decorator.onRequest(requestSpec, span));

                tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new MutableHeadersInjector(requestSpec));

                spanReference.set(span);
            });

            spec.responseIntercept(response -> {
                Span span = spanReference.get();
                if (span != null) {
                    clientSpanDecorators.forEach(decorator -> decorator.onResponse(response, span));

                    span.finish();
                }
            });

            spec.errorIntercept(error -> {
                Span span = spanReference.get();
                if (span != null) {
                    clientSpanDecorators.forEach(decorator -> decorator.onError(error, span));

                    span.finish();
                }
            });
        });

        ctx.next(Registry.single(client));
    }
}
