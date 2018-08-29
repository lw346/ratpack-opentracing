package uk.me.lwood.ratpack.opentracing.client;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;
import uk.me.lwood.ratpack.opentracing.server.ServerSpanDecorator;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Decorates the span at each phase of execution.  Decorators should not call finish().
 */
public interface ClientSpanDecorator {

    /**
     * Decorate span before request is fired.
     *
     * @param request request
     * @param span span to decorate
     */
    void onRequest(RequestSpec request, Span span);

    /**
     * Decorate span after response is received.
     *
     * @param response response
     * @param span span to decorate
     */
    void onResponse(HttpResponse response, Span span);

    /**
     *  Decorate span span on error e.g. {@link java.net.UnknownHostException}/
     *
     * @param ex exception
     * @param span span to decorate
     */
    void onError(Throwable ex, Span span);

    ClientSpanDecorator StandardTags = new ClientSpanDecorator() {
        @Override
        public void onRequest(RequestSpec request, Span span) {
            Tags.HTTP_METHOD.set(span, request.getMethod().getName());

            URI uri = request.getUri();
            Tags.HTTP_URL.set(span, uri.getPath());
            Tags.PEER_PORT.set(span, uri.getPort() == -1 ? 80 : uri.getPort());
            Tags.PEER_HOSTNAME.set(span, uri.getHost());
        }

        @Override
        public void onResponse(HttpResponse response, Span span) {
            Tags.HTTP_STATUS.set(span, response.getStatusCode());
        }

        @Override
        public void onError(Throwable ex, Span span) {
            Tags.ERROR.set(span, Boolean.TRUE);

            Map<String, Object> errorLogs = new HashMap<>(2);
            errorLogs.put("event", Tags.ERROR.getKey());
            errorLogs.put("error.object", ex);
            span.log(errorLogs);
        }
    };

}
