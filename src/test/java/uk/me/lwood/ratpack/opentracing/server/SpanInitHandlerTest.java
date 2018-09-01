package uk.me.lwood.ratpack.opentracing.server;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Test;
import ratpack.error.ServerErrorHandler;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpanInitHandlerTest {
    private static MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.TEXT_MAP);

    private SpanInitHandler handler = new SpanInitHandler(
            mockTracer,
            singletonList(ServerSpanDecorator.StandardTags),
            ServerOperationNameProvider.MethodAndPath);

    @After
    public void cleanUp() {
        mockTracer.reset();
    }

    @Test
    public void testStandardTags() throws Exception {
        EmbeddedApp.fromHandlers(chain -> chain.all(handler).all(ctx -> ctx.render("OK")))
                .test(client -> client.request(request -> request.headers(headers -> headers
                        .add("spanid", "12345")
                        .add("traceid", "54321")
                )));

        List<MockSpan> spans = mockTracer.finishedSpans();
        assertEquals(1, spans.size());

        MockSpan span = spans.get(0);
        assertEquals(SpanInitHandler.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, span.tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals("GET /", span.operationName());
        assertEquals("GET", span.tags().get(Tags.HTTP_METHOD.getKey()));
        assertEquals("/", span.tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(200, span.tags().get(Tags.HTTP_STATUS.getKey()));
        assertEquals(0, span.logEntries().size());
    }

    @Test
    public void testException() throws Exception {
        final Exception ex = new Exception();

        EmbeddedApp.fromHandlers(chain -> chain.all(handler).all(ctx -> {
                    throw ex;
                }))
                .test(client -> client.request(request -> request.headers(headers -> headers
                        .add("spanid", "12345")
                        .add("traceid", "54321")
                )));

        List<MockSpan> spans = mockTracer.finishedSpans();
        assertEquals(1, spans.size());

        MockSpan span = spans.get(0);
        assertEquals(SpanInitHandler.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, span.tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals("GET /", span.operationName());
        assertEquals("GET", span.tags().get(Tags.HTTP_METHOD.getKey()));
        assertEquals("/", span.tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(500, span.tags().get(Tags.HTTP_STATUS.getKey()));
        assertEquals(1, span.logEntries().size());
        assertEquals(Boolean.TRUE, span.tags().get(Tags.ERROR.getKey()));

        MockSpan.LogEntry logEntry = span.logEntries().get(0);
        assertEquals(Tags.ERROR.getKey(), logEntry.fields().get("event"));
        assertEquals(ex, logEntry.fields().get("error.object"));
    }

    @Test
    public void testExceptionIsPropagatedToErroHandler() throws Exception {
        final AtomicReference<Throwable> sawException = new AtomicReference<>();
        final AtomicBoolean errorHandlerCalled = new AtomicBoolean(false);
        final ServerErrorHandler errorHandler = (context, throwable) -> {
            errorHandlerCalled.set(true);
            sawException.set(throwable);
        };
        final Exception ex = new Exception();

        EmbeddedApp.fromHandlers(chain -> chain
                .all(ctx -> ctx.next(Registry.single(errorHandler)))
                .all(handler)
                .all(ctx -> {
                    throw ex;
                }))
                .test(client -> client.request(request -> request.headers(headers -> headers
                        .add("spanid", "12345")
                        .add("traceid", "54321")
                )));

        assertTrue("Delegated error handler was not invoked", errorHandlerCalled.get());
        assertEquals(ex, sawException.get());
    }

    @Test
    public void testExceptionIsNotPropagatedIfNoErroHandler() throws Exception {
        final Exception ex = new Exception();

        EmbeddedApp.fromHandlers(chain -> chain
                .all(ctx -> ctx.next(Registry.single(ServerErrorHandler.class, (ServerErrorHandler) null)))
                .all(handler)
                .all(ctx -> {
                    throw ex;
                }))
                .test(client -> client.request(request -> request.headers(headers -> headers
                        .add("spanid", "12345")
                        .add("traceid", "54321")
                )));

        List<MockSpan> spans = mockTracer.finishedSpans();
        assertEquals(1, spans.size());
    }
}