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
    class MethodAndPath implements ClientOperationNameProvider {
        @Override
        public String provideOperationName(RequestSpec requestSpec) {
            return requestSpec.getMethod().getName() + " " + requestSpec.getUri().getPath();
        }
    }
}
