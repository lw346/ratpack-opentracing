package uk.me.lwood.ratpack.opentracing.client;

import ratpack.http.client.RequestSpec;

public interface ClientOperationNameProvider {
    String provideOperationName(RequestSpec requestSpec);

    class MethodAndPath implements ClientOperationNameProvider {
        @Override
        public String provideOperationName(RequestSpec requestSpec) {
            return requestSpec.getMethod().getName() + " " + requestSpec.getUri().getPath();
        }
    }
}
