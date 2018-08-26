package uk.me.lwood.ratpack.opentracing.server;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.registry.Registry;

import java.util.List;

/**
 * Initialises a server side span on the {@link Tracer} by attempting to extract a parent span from the HTTP headers
 * sent over the wire.
 *
 * {@link ServerSpanDecorator}s are used to decorate the resulting server span, and {@link ServerOperationNameProvider}
 * is used to determine the name of the operation that this span represents.
 *
 * This handler should be inserted at the start of the chain using {@link ratpack.handling.Chain#all(Handler)}.
 */
public class SpanInitHandler implements Handler {
    static final String COMPONENT_NAME = "ratpack-server";

    private final Tracer tracer;
    private final List<ServerSpanDecorator> serverSpanDecorators;
    private final ServerOperationNameProvider operationNameProvider;

    public SpanInitHandler(Tracer tracer, List<ServerSpanDecorator> serverSpanDecorators, ServerOperationNameProvider operationNameProvider) {
        this.tracer = tracer;
        this.serverSpanDecorators = serverSpanDecorators;
        this.operationNameProvider = operationNameProvider;
    }

    @Override
    public void handle(Context context) {
        SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
                new MultiValueMapExtractor(context.getRequest().getHeaders().asMultiValueMap()));

        Span span = tracer.buildSpan(operationNameProvider.provideOperationName(context))
                .ignoreActiveSpan()
                .asChildOf(extractedContext)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
                .start();

        serverSpanDecorators.forEach(decorator -> decorator.onRequest(context.getRequest(), span));

        context.onClose(requestOutcome -> {
            serverSpanDecorators.forEach(decorator -> decorator.onResponse(requestOutcome, span));
            span.finish();
        });

        context.next(Registry.builder()
                .add(span)
                .add(new InterceptingErrorHandler(context.get(ServerErrorHandler.class), span))
                .build());
    }

    private class InterceptingErrorHandler implements ServerErrorHandler {
        private final ServerErrorHandler delegate;
        private final Span span;

        private InterceptingErrorHandler(ServerErrorHandler delegate, Span span) {
            this.delegate = delegate;
            this.span = span;
        }

        @Override
        public void error(Context context, Throwable throwable) throws Exception {
            serverSpanDecorators.forEach(decorator -> decorator.onError(throwable, span));

            if (delegate != null) {
                delegate.error(context, throwable);
            }
        }
    }
}
