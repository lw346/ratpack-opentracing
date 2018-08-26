package uk.me.lwood.ratpack.opentracing.client;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;
import ratpack.registry.Registry;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SpanPropagationHandler implements Handler {
    static final String COMPONENT_NAME = "ratpack-client";

    private final Tracer tracer;
    private final List<ClientSpanDecorator> clientSpanDecorators;
    private final ClientOperationNameProvider operationNameProvider;

    @Inject
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
