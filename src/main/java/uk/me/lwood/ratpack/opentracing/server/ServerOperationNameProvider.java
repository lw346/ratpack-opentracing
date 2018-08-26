package uk.me.lwood.ratpack.opentracing.server;

import ratpack.handling.Context;
import ratpack.http.Request;

public interface ServerOperationNameProvider {
    public String provideOperationName(Context context);

    class MethodAndPath implements ServerOperationNameProvider {
        @Override
        public String provideOperationName(Context context) {
            Request request = context.getRequest();
            return request.getMethod().getName() + " " + request.getPath();
        }
    }
}
