# Ratpack OpenTracing support

Provides utility classes to hook into Ratpack and provide traces via OpenTracing.

To use this library effectively, you will need to instantiate a `Tracer` implementation.

## Usage

To create a Server Span for the duration of the request, and to extract any parent span from the incoming request
headers, you will need to add the `SpanInitHandler` to your chain:

```java
Tracer tracer = ...;
List<ServerSpanDecorator> decorators = singletonList(new ServerSpanDecorator.StandardTags());
ServerOperationNameProvider nameProvider = new ServerOperationNameProvider.MethodAndPath();

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
List<ClientSpanDecorator> decorators = singletonList(new ClientSpanDecorator.StandardTags());
ClientOperationNameProvider nameProvider = new ClientOperationNameProvider.MethodAndPath();

RatpackServer server = RatpackServer.of(
    chain -> chain
        .all(new SpanInitHandler(...))
        .all(new SpanPropagationHandler(tracer, decorators, nameProvider)),
    ...,
    ...
)
```