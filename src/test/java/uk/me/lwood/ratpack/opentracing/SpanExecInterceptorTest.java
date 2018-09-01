package uk.me.lwood.ratpack.opentracing;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Test;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpanExecInterceptorTest {
    private static MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.TEXT_MAP);

    private final SpanExecInterceptor interceptor = new SpanExecInterceptor(mockTracer);

    @After
    public void setUp() {
        mockTracer.reset();
    }

    @Test
    public void testSpanIsPropagated() throws Exception {
        final MockSpan span = mockTracer.buildSpan("test-span").start();

        final AtomicReference<Span> sawSpan = new AtomicReference<>();
        EmbeddedApp.fromHandlers(chain -> chain
                .all(ctx -> ctx.next(Registry.single(Span.class, span)))
                .all(ctx -> ctx.getExecution().addInterceptor(interceptor, () -> ctx.next()))
                .all(ctx -> {
                    sawSpan.set(mockTracer.activeSpan());
                    ctx.render("OK");
                }))
                .test(client -> assertEquals("OK", client.getText()));

        assertEquals(span, sawSpan.get());
    }

    @Test
    public void testNoSpanIsPropagated() throws Exception {
        final AtomicReference<Span> sawSpan = new AtomicReference<>();
        EmbeddedApp.fromHandlers(chain -> chain
                .all(ctx -> ctx.getExecution().addInterceptor(interceptor, () -> ctx.next()))
                .all(ctx -> {
                    sawSpan.set(mockTracer.activeSpan());
                    ctx.render("OK");
                }))
                .test(client -> assertEquals("OK", client.getText()));

        assertNull(sawSpan.get());
    }

}