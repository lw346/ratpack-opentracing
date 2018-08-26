package uk.me.lwood.ratpack.opentracing.server;

import io.opentracing.propagation.TextMap;
import ratpack.util.MultiValueMap;

import java.util.Iterator;
import java.util.Map;

class MultiValueMapExtractor implements TextMap {
    private final MultiValueMap<String, String> map;

    MultiValueMapExtractor(MultiValueMap<String, String> map) {
        this.map = map;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
        throw new UnsupportedOperationException();
    }
}
