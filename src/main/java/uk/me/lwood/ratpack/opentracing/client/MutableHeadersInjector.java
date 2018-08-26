package uk.me.lwood.ratpack.opentracing.client;

import io.opentracing.propagation.TextMap;
import ratpack.http.client.RequestSpec;

import java.util.Iterator;
import java.util.Map;

class MutableHeadersInjector implements TextMap {
    private final RequestSpec requestSpec;

    public MutableHeadersInjector(RequestSpec requestSpec) {
        this.requestSpec = requestSpec;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
    }

    @Override
    public void put(String key, String value) {
        requestSpec.getHeaders().add(key, value);
    }
}
