# Ratpack OpenTracing support

Provides utility classes to hook into Ratpack and provide traces via OpenTracing.

To use this library effectively, you will need to instantiate a `Tracer` implementation.

## Usage

To create a Server Span for the duration of the request, and to extract any parent span from the incoming request
headers, you will need to add the `SpanInitHandler` to your chain:

```java
Tracer tracer = ...;
List<ServerSpanDecorator> decorators = singletonList(ServerSpanDecorator.StandardTags);
ServerOperationNameProvider nameProvider = ServerOperationNameProvider.MethodAndPath;

RatpackServer server = RatpackServer.of(
    chain -> chain.all(new SpanInitHandler(tracer, decorators, nameProvider)),
    ...,
    ...
)
```

In order to support other libraries that expect the active span to be set in a thread-local context, you will need
to install the `SpanExecInterceptor`.

To propagate the span information to downstream clients using Ratpack's built-in `HttpClient`, you can add the
`SpanPropagationHandler` to your chain:

```java
Tracer tracer = ...;
List<ClientSpanDecorator> decorators = singletonList(ClientSpanDecorator.StandardTags);
ClientOperationNameProvider nameProvider = ClientOperationNameProvider.MethodAndPath;

RatpackServer server = RatpackServer.of(
    chain -> chain
        .all(new SpanInitHandler(...))
        .all(new SpanPropagationHandler(tracer, decorators, nameProvider)),
    ...,
    ...
)
```

## Integrating with Jaeger

```java
public class JaegerModule extends AbstractModule {
    @Override
    public void configure() {
        JaegerTracer tracer = Configuration.fromEnv("my-service")
            .getTracer();

        // Expose the Tracer to other dependencies
        bind(Tracer.class).toInstance(tracer);

        // Install the interceptor and bind our custom handlers
        bind(SpanExecInterceptor.class).toInstance(new SpanExecInterceptor(tracer));
        bind(SpanPropagationHandler.class).toInstance(new SpanPropagationHandler(
            tracer,
            singletonList(ClientSpanDecorator.StandardTags),
            ServerOperationNameProvider.MethodAndPath));
        bind(SpanInitHandler.class).toInstance(new SpanInitHandler(
            tracer,
            singletonList(ServerSpanDecorator.StandardTags),
            ServerOperationNameProvider.MethodAndPath));

        // Ensure that the reporter and sampler are stopped on shutdown
        bind(JaegerTracerService.class).toInstance(new JaegerTracerService(tracer));
    }

    private class JaegerTracerService implements Service {
        private final JaegerTracer tracer;

        public JaegerTracerService(JaegerTracer tracer) {
            this.tracer = tracer;
        }

        public void onStart(StartEvent event) {
            // do nothing
        }

        public void onStop(StopEvent event) {
            tracer.close();
        }
    }
}
```