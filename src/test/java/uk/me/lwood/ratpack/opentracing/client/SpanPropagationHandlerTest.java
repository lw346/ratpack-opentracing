package uk.me.lwood.ratpack.opentracing.client;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Test;
import ratpack.http.Headers;
import ratpack.http.client.HttpClient;
import ratpack.test.embed.EmbeddedApp;
import uk.me.lwood.ratpack.opentracing.server.ServerOperationNameProvider;
import uk.me.lwood.ratpack.opentracing.server.ServerSpanDecorator;
import uk.me.lwood.ratpack.opentracing.server.SpanInitHandler;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpanPropagationHandlerTest {
    private static MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.TEXT_MAP);

    private SpanInitHandler initHandler = new SpanInitHandler(
            mockTracer,
            singletonList(ServerSpanDecorator.StandardTags),
            ServerOperationNameProvider.MethodAndPath);

    private SpanPropagationHandler handler = new SpanPropagationHandler(
            mockTracer,
            singletonList(ClientSpanDecorator.StandardTags),
            ClientOperationNameProvider.MethodAndPath
    );

    @After
    public void cleanUp() {
        mockTracer.reset();
    }

    @Test
    public void testStandardTags() throws Exception {
        URI address;
        AtomicReference<Headers> headersRef = new AtomicReference<>();
        try (EmbeddedApp otherApp = EmbeddedApp.fromHandler(ctx -> {
            headersRef.set(ctx.getRequest().getHeaders());
            ctx.render("OK");
        })) {
            address = otherApp.getAddress();

            EmbeddedApp.fromHandlers(chain -> chain
                    .all(initHandler)
                    .all(handler)
                    .all(ctx -> ctx.get(HttpClient.class).get(address).then(response -> ctx.render("OK")))
            ).test(client -> client.get());
        }

        List<MockSpan> spans = mockTracer.finishedSpans();
        assertEquals(2, spans.size());

        MockSpan clientSpan = spans.get(1);
        assertEquals(SpanPropagationHandler.COMPONENT_NAME, clientSpan.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_CLIENT, clientSpan.tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals("GET /", clientSpan.operationName());
        assertEquals("GET", clientSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        assertEquals("/", clientSpan.tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(200, clientSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        assertEquals(address.getHost(), clientSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        assertEquals(address.getPort(), clientSpan.tags().get(Tags.PEER_PORT.getKey()));
        assertEquals(0, clientSpan.logEntries().size());

        Headers headers = headersRef.get();
        assertTrue(headers.contains("traceId"));
        assertTrue(headers.contains("spanId"));
    }

    @Test
    public void testExceptionIntercepting() throws Exception {
        URI address = new URI("http://localhost:20000/");

        EmbeddedApp.fromHandlers(chain -> chain
                .all(initHandler)
                .all(handler)
                .all(ctx -> ctx.get(HttpClient.class).get(address).then(response -> ctx.render("OK")))
        ).test(client -> client.get());

        List<MockSpan> spans = mockTracer.finishedSpans();
        assertEquals(2, spans.size());

        MockSpan clientSpan = spans.get(0);
        assertEquals(SpanPropagationHandler.COMPONENT_NAME, clientSpan.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_CLIENT, clientSpan.tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals("GET /", clientSpan.operationName());
        assertEquals("GET", clientSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        assertEquals("/", clientSpan.tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(address.getHost(), clientSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        assertEquals(address.getPort(), clientSpan.tags().get(Tags.PEER_PORT.getKey()));
        assertEquals(Boolean.TRUE, clientSpan.tags().get(Tags.ERROR.getKey()));

        MockSpan.LogEntry logEntry = clientSpan.logEntries().get(0);
        assertEquals(Tags.ERROR.getKey(), logEntry.fields().get("event"));
        Exception exception = (Exception) logEntry.fields().get("error.object");
        assertTrue(exception.getMessage().contains("Connection refused"));
    }
}