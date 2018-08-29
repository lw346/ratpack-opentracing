package uk.me.lwood.ratpack.opentracing.server;

import ratpack.handling.Context;
import ratpack.http.Request;

/**
 * Provides the operation-name for a server span initiated by the {@link SpanInitHandler}
 */
public interface ServerOperationNameProvider {
    String provideOperationName(Context context);

    /**
     * Produces "METHOD /path" as the operation-name
     */
    ServerOperationNameProvider MethodAndPath = context -> {
        Request request = context.getRequest();
        return request.getMethod().getName() + " " + request.getUri();
    };
}
