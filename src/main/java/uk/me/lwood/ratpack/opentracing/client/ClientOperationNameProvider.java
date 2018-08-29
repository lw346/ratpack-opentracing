package uk.me.lwood.ratpack.opentracing.client;

import ratpack.http.client.RequestSpec;

/**
 * Provides the operation-name for a client span initiated inside a HttpClient
 */
public interface ClientOperationNameProvider {
    String provideOperationName(RequestSpec requestSpec);

    /**
     * Produces "METHOD /path" as the operation-name
     */
    ClientOperationNameProvider MethodAndPath = requestSpec ->
            requestSpec.getMethod().getName() + " " + requestSpec.getUri().getPath();
}
