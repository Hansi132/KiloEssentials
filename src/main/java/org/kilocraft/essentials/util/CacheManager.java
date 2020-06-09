package org.kilocraft.essentials.util;

import org.kilocraft.essentials.api.util.Cached;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class CacheManager {
    private static final Map<String, Cached<?>> map = new HashMap<>();

    public static Map<String, Cached<?>> getMap() {
        return map;
    }

    public static void cache(Cached<?>... cached) {
        for (Cached<?> c : Objects.requireNonNull(cached, "Cache entry must not be null!")) {
            map.put(c.getId(), c);
        }
    }

    public static <T> Cached<T> get(String id) {
        return (Cached<T>) map.get(id);
    }

    public static boolean isCached(String id) {
        return map.containsKey(id);
    }

    public static boolean shouldUse(String id) {
        boolean should = isCached(id) && get(id).isValid();
        if (!should) {
            map.remove(id);
        }
        return should;
    }

    public static <T> void getAndRun(String id, Consumer<Cached<T>> consumer) {
        Cached<T> cached = get(id);
        consumer.accept(cached);
    }

}
