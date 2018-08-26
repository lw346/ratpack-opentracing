package uk.me.lwood.ratpack.opentracing.server;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import ratpack.handling.RequestOutcome;
import ratpack.http.Request;

import java.util.HashMap;
import java.util.Map;

public interface ServerSpanDecorator {

    /**
     * Decorate span before request is fired.
     *
     * @param request request
     * @param span span to decorate
     */
    void onRequest(Request request, Span span);

    /**
     * Decorate span after response is received.
     *
     * @param response response
     * @param span span to decorate
     */
    void onResponse(RequestOutcome response, Span span);

    /**
     *  Decorate span span on error e.g. {@link java.net.UnknownHostException}/
     *
     * @param ex exception
     * @param span span to decorate
     */
    void onError(Throwable ex, Span span);

    class StandardTags implements ServerSpanDecorator {
        @Override
        public void onRequest(Request request, Span span) {
            Tags.HTTP_METHOD.set(span, request.getMethod().getName());
            Tags.HTTP_URL.set(span, request.getUri());
        }

        @Override
        public void onResponse(RequestOutcome response, Span span) {
            Tags.HTTP_STATUS.set(span, response.getResponse().getStatus().getCode());
        }

        @Override
        public void onError(Throwable ex, Span span) {
            Tags.ERROR.set(span, Boolean.TRUE);

            Map<String, Object> errorLogs = new HashMap<>(2);
            errorLogs.put("event", Tags.ERROR.getKey());
            errorLogs.put("error.object", ex);
            span.log(errorLogs);
        }
    }

}
