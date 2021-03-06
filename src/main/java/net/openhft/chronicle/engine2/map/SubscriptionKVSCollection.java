package net.openhft.chronicle.engine2.map;

import net.openhft.chronicle.engine2.api.Subscriber;
import net.openhft.chronicle.engine2.api.Subscription;
import net.openhft.chronicle.engine2.api.TopicSubscriber;
import net.openhft.chronicle.engine2.api.map.KeyValueStore;
import net.openhft.chronicle.engine2.api.map.MapEvent;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by peter on 22/05/15.
 */
public class SubscriptionKVSCollection<K, V> implements Subscription {
    final Set<TopicSubscriber<V>> topicSubscribers = new CopyOnWriteArraySet<>();
    final Set<Subscriber<KeyValueStore.Entry<K, V>>> subscribers = new CopyOnWriteArraySet<>();
    final Set<Subscriber<K>> keySubscribers = new CopyOnWriteArraySet<>();
    boolean hasSubscribers = false;
    final KeyValueStore<K, V> kvStore;

    public SubscriptionKVSCollection(KeyValueStore<K, V> kvStore) {
        this.kvStore = kvStore;
    }

    public void notifyUpdate(K key, V oldValue, V value) {
        if (hasSubscribers)
            notifyUpdate0(key, oldValue, value);
    }

    private void notifyUpdate0(K key, V oldValue, V value) {
        if (!topicSubscribers.isEmpty()) {
            String key2 = key.toString();
            topicSubscribers.forEach(ts -> ts.on(key2, value));
        }
        if (!subscribers.isEmpty()) {
            if (oldValue == null) {
                InsertedEvent<K, V> inserted = InsertedEvent.of(key, value);
                subscribers.forEach(s -> s.on(inserted));

            } else {
                UpdatedEvent<K, V> updated = UpdatedEvent.of(key, oldValue, value);
                subscribers.forEach(s -> s.on(updated));
            }
        }
        if (!keySubscribers.isEmpty()) {
            keySubscribers.forEach(s -> s.on(key));
        }
    }

    public void notifyRemoval(K key, V oldValue) {
        if (hasSubscribers)
            notifyRemoval0(key, oldValue);
    }

    private void notifyRemoval0(K key, V oldValue) {
        if (!topicSubscribers.isEmpty()) {
            String key2 = key.toString();
            topicSubscribers.forEach(ts -> ts.on(key2, null));
        }
        if (!subscribers.isEmpty()) {
            RemovedEvent<K, V> removed = RemovedEvent.of(key, oldValue);
            subscribers.forEach(s -> s.on(removed));
        }
        if (!keySubscribers.isEmpty()) {
            keySubscribers.forEach(s -> s.on(key));
        }
    }

    @Override
    public <E> void registerSubscriber(Class<E> eClass, Subscriber<E> subscriber, String query) {
        boolean bootstrap = query.contains("bootstrap=true");
        if (eClass == KeyValueStore.Entry.class || eClass == MapEvent.class) {
            subscribers.add((Subscriber) subscriber);
            if (bootstrap) {
                for (int i = 0; i < kvStore.segments(); i++)
                    kvStore.entriesFor(i, e -> subscriber.on((E) InsertedEvent.of(e.key(), e.value())));
            }
        } else {
            keySubscribers.add((Subscriber<K>) subscriber);
            if (bootstrap) {
                for (int i = 0; i < kvStore.segments(); i++)
                    kvStore.keysFor(i, k -> subscriber.on((E) k));
            }
        }
        hasSubscribers = true;
    }

    @Override
    public <E> void registerSubscriber(Class<E> eClass, TopicSubscriber<E> subscriber, String query) {
        boolean bootstrap = query.contains("bootstrap=true");
        topicSubscribers.add((TopicSubscriber<V>) subscriber);
        if (bootstrap) {
            for (int i = 0; i < kvStore.segments(); i++)
                kvStore.entriesFor(i, (KeyValueStore.Entry<K, V> e) -> subscriber.on(e.key().toString(), (E) e.value()));
        }
        hasSubscribers = true;
    }

    @Override
    public <E> void unregisterSubscriber(Class<E> eClass, Subscriber<E> subscriber, String query) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public <E> void unregisterSubscriber(Class<E> eClass, TopicSubscriber<E> subscriber, String query) {
        topicSubscribers.remove(subscriber);
        hasSubscribers = !topicSubscribers.isEmpty() && !subscribers.isEmpty();
    }
}
