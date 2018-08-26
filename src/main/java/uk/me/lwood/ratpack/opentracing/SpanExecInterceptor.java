package uk.me.lwood.ratpack.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import ratpack.exec.ExecInterceptor;
import ratpack.exec.Execution;
import ratpack.func.Block;
import ratpack.handling.Context;

import javax.inject.Inject;

/**
 * Intercepts the execution of any blocks to set the active span on the Tracer implementation.  This allows users
 * to invoke {@link Tracer#buildSpan(String)} and have it automatically pick up the active span as the parent span.
 */
public class SpanExecInterceptor implements ExecInterceptor {
    private final Tracer tracer;

    @Inject
    public SpanExecInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void intercept(Execution execution, ExecType execType, Block block) throws Exception {
        Span span = execution.maybeGet(Context.class).map(context -> context.get(Span.class)).orElse(null);
        if (span != null) {
            try (Scope scope = tracer.scopeManager().activate(span, false)) {
                block.execute();
            }
        }
        else {
            block.execute();
        }
    }
}
