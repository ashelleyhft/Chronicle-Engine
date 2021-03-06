package net.openhft.chronicle.engine2;

import net.openhft.chronicle.engine2.api.*;
import net.openhft.chronicle.engine2.api.map.MapEvent;
import net.openhft.chronicle.engine2.map.InsertedEvent;
import net.openhft.chronicle.engine2.map.RemovedEvent;
import net.openhft.chronicle.engine2.map.UpdatedEvent;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static net.openhft.chronicle.engine2.Chassis.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Created by peter on 22/05/15.
 */
public class ChassisTest {
    @Before
    public void setUp() {
        resetChassis();
    }

    @Test
    public void simpleGetMapView() {
        ConcurrentMap<String, String> map = acquireMap("map-name", String.class, String.class);

        registerTopicSubscriber("map-name", String.class, (t, e) -> System.out.println("{ key: " + t + ", event: " + e + " }"));

        map.put("Hello", "World");

        ConcurrentMap<String, String> map2 = acquireMap("map-name", String.class, String.class);
        assertSame(map, map2);

        map2.put("Bye", "soon");

        map2.put("Bye", "now.");
    }

    @Test
    public void subscription() {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        Subscriber<String> subscriber = createMock(Subscriber.class);
        subscriber.on("Key-1");
        subscriber.on("Key-2");
        replay(subscriber);
        registerSubscriber("map-name?bootstrap=true", String.class, subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.on("Topic-1");
        replay(subscriber);

        TopicPublisher<String> publisher = acquireTopicPublisher("map-name", String.class);
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.on("Hello");
        subscriber.on("Bye");
        subscriber.on("Key-1");
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());

        // check the contents.
        assertEquals("Topic-1=Message-1\n" +
                        "Key-2=Value-2\n" +
                        "Hello=World\n" +
                        "Bye=soon",
                map.entrySet().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));

        assertEquals("Topic-1, Key-2, Hello, Bye",
                map.keySet().stream()
                        .collect(Collectors.joining(", ")));

        assertEquals("Message-1, Value-2, World, soon",
                map.values().stream()
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void keySubscription() {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds the old value
        Subscriber<String> subscriber = createMock(Subscriber.class);
        subscriber.on("Value-1");
        replay(subscriber);
        registerSubscriber("map-name/Key-1?bootstrap=true", String.class, subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.on("Message-1");
        replay(subscriber);

        TopicPublisher<String> publisher = acquireTopicPublisher("map-name", String.class);
        publisher.publish("Key-1", "Message-1");
        publisher.publish("Key-2", "Message-2");
        verify(subscriber);
        reset(subscriber);

        subscriber.on("Bye");
        subscriber.on(null);
        replay(subscriber);

        // test plain puts trigger events
        map.put("Key-1", "Bye");
        map.put("Key-3", "Another");
        map.remove("Key-1");
        verify(subscriber);
    }

    @Test
    public void topicSubscription() {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        TopicSubscriber<String> subscriber = createMock(TopicSubscriber.class);
        subscriber.on("Key-1", "Value-1");
        subscriber.on("Key-2", "Value-2");
        replay(subscriber);
        registerTopicSubscriber("map-name?bootstrap=true", String.class, subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.on("Topic-1", "Message-1");
        replay(subscriber);

        TopicPublisher<String> publisher = acquireTopicPublisher("map-name", String.class);
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.on("Hello", "World");
        subscriber.on("Bye", "soon");
        subscriber.on("Key-1", null);
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());

        // check the contents.
        assertEquals("Topic-1=Message-1\n" +
                        "Key-2=Value-2\n" +
                        "Hello=World\n" +
                        "Bye=soon",
                map.entrySet().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));

        assertEquals("Topic-1, Key-2, Hello, Bye",
                map.keySet().stream()
                        .collect(Collectors.joining(", ")));

        assertEquals("Message-1, Value-2, World, soon",
                map.values().stream()
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void entrySubscription() {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        Subscriber<MapEvent<String, String>> subscriber = createMock(Subscriber.class);
        subscriber.on(InsertedEvent.of("Key-1", "Value-1"));
        subscriber.on(InsertedEvent.of("Key-2", "Value-2"));
        replay(subscriber);
        registerSubscriber("map-name?bootstrap=true", MapEvent.class, (Subscriber) subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.on(UpdatedEvent.of("Key-1", "Value-1", "Message-1"));
        subscriber.on(InsertedEvent.of("Topic-1", "Message-1"));
        replay(subscriber);

        TopicPublisher<String> publisher = acquireTopicPublisher("map-name", String.class);
        publisher.publish("Key-1", "Message-1");
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.on(InsertedEvent.of("Hello", "World"));
        subscriber.on(InsertedEvent.of("Bye", "soon"));
        subscriber.on(RemovedEvent.of("Key-1", "Message-1"));
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());
    }

    @Test
    public void newNode() {
        Asset group = acquireAsset("group", Void.class, null, null);
        Asset subgroup = acquireAsset("group/sub-group?option=unknown", Void.class, null, null);
        assertEquals("group/sub-group", subgroup.fullName());

        Asset group2 = acquireAsset("group2/sub-group?who=knows", Void.class, null, null);
        assertEquals("group2/sub-group", group2.fullName());
    }

    @Test(expected = AssetNotFoundException.class)
    public void noAsset() {
        registerTopicSubscriber("map-name", String.class, (t, e) -> System.out.println("{ key: " + t + ", event: " + e + " }"));
    }

    @Test(expected = AssetNotFoundException.class)
    public void noInterceptor() {
        Asset asset = acquireAsset("", null, null, null);

        asset.acquireInterceptor(MyInterceptor.class);
    }

    @Test
    public void generateInterceptor() {
        Asset asset = acquireAsset("", null, null, null);

        asset.registerFactory(Interceptor.class, (FactoryContext context) -> {
            assertEquals(MyInterceptor.class, context.type());
            return new MyInterceptor();
        });
        MyInterceptor mi = asset.acquireInterceptor(MyInterceptor.class);
        MyInterceptor mi2 = asset.acquireInterceptor(MyInterceptor.class);
        assertNotNull(mi);
        assertSame(mi, mi2);
    }

    static class MyInterceptor implements Interceptor {

    }
}
