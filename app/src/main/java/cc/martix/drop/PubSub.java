package cc.martix.drop;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PubSub {
    private PubSub() {
    }

    public static final PubSub instance = new PubSub();

    @SuppressWarnings("rawtypes")
    private final Map<String, List<Callback>> events = new LinkedHashMap<>();

    /* 订阅 */
    @SuppressWarnings("rawtypes")

    public <T> void subscribe(String ev, Callback<T> callback) {
        List<Callback> runnables = events.computeIfAbsent(ev, k -> new LinkedList<>());
        runnables.add(callback);
    }

    /* 发布 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> void _publish(final String ev, final boolean needClear, T param) {
        final List<Callback> callbacks = events.get(ev);
        /* 同步调用每个callback，防止线程限制问题 */
        if (callbacks != null && !callbacks.isEmpty()) {
            for (Callback callback : callbacks) {
                callback.run(param);
            }
            if (needClear) {
                events.remove(ev);
            }
        }
    }

    /* 发布，不清除订阅者 */
    public <T> void publish(String ev, T param) {
        _publish(ev, false, param);
    }

    public <T> void publishAndClear(String ev, T param) {
        _publish(ev, true, param);
    }

    public interface Callback<T> {
        void run(T param);
    }
}
