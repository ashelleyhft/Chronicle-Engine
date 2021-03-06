/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.engine.client.internal;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.ChronicleContext;
import net.openhft.chronicle.engine.FilePerKeyMapSubscription;
import net.openhft.chronicle.engine.MapEventListener;
import net.openhft.chronicle.engine.Subscription;
import net.openhft.chronicle.engine.old.ChronicleCluster;
import net.openhft.chronicle.engine.old.ChronicleThreadPool;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.EngineMap;
import net.openhft.chronicle.map.FilePerKeyMap;
import net.openhft.chronicle.map.MapWireConnectionHub;
import net.openhft.chronicle.set.ChronicleSet;
import net.openhft.chronicle.wire.TextWire;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.Collections.synchronizedMap;
import static net.openhft.chronicle.map.ChronicleMapBuilder.of;

/**
 * Created by peter.lawrey on 09/10/14.
 */
public class ChronicleEngine implements ChronicleContext, Closeable {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ChronicleEngine.class);

    //    private final Map<String, ChronicleQueue> queues = Collections.synchronizedMap(new
    //        LinkedHashMap<>());
    private final Map<String, Map<byte[], byte[]>> underlyingMaps
            = synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ChronicleMap> maps = synchronizedMap(new LinkedHashMap<>());

    private final Map<String, ChronicleMap<CharSequence, CharSequence>> chronStringMap = synchronizedMap(new LinkedHashMap<>());
    private final Map<String, FilePerKeyMap> fpMaps = synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ChronicleSet> sets = synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ChronicleThreadPool> threadPools = synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ChronicleCluster> clusters = synchronizedMap(new LinkedHashMap<>());
    private MapWireConnectionHub mapWireConnectionHub = null;

    public ChronicleEngine() {
        // todo config port and identifiers
        final byte localIdentifier = (byte) 1;
        final int serverPort = 8085;

        try {
            mapWireConnectionHub = new MapWireConnectionHub(localIdentifier, serverPort);
        } catch (IOException e) {
            LOG.error("", e);
        }

    }
   /*
    public void setQueue(String name, ChronicleQueue chronicle) {
        queues.put(name, chronicle);
    }

    @Override
    public ChronicleQueue getQueue(String name) {
        return queues.get(name);
    }
    */

    public void setMap(String name, ChronicleMap map) throws IOException {
        maps.put(name, map);
    }

    @Override
    public <K, V> ChronicleMap<K, V> getMap(String name, final Class<K> kClass, final Class<V> vClass)
            throws IOException {

        // TODO make this configurable.
        long entries = 1000;
        final int maxValueSize = 4 << 10;
        final int maxKeySize = 64;
        final boolean putReturnsNull = true;
        final boolean removeReturnsNull = true;

        // if its a string map the we will use the string map directly
        if (CharSequence.class.isAssignableFrom(kClass) &&
                CharSequence.class.isAssignableFrom(vClass)) {
            if (String.class.isAssignableFrom(kClass) &&
                    String.class.isAssignableFrom(vClass)) {
                throw new UnsupportedOperationException("Please use a Map<CharSequence,CharSequence> rather than a Map<String,String>");
            }

            ChronicleMap map = maps.get(name);
            if (map != null)
                return map;
            final ChronicleMap<CharSequence, CharSequence> stringMap = chronStringMap.computeIfAbsent(name,
                    k -> {
                        try {

                            return mapWireConnectionHub.acquireMap(name, () -> of(
                                    CharSequence.class,
                                    CharSequence.class)
                                    .entries(entries)
                                    .averageValueSize(maxValueSize)
                                    .averageKeySize(maxKeySize)
                                    .putReturnsNull(putReturnsNull)
                                    .removeReturnsNull(removeReturnsNull)
                                    .instance());
                        } catch (IOException ioe) {
                            throw Jvm.rethrow(ioe);
                        }
                    });

            return (ChronicleMap) stringMap;
        }

        @SuppressWarnings("unchecked")
        Map<byte[], byte[]> underlyingMap = underlyingMaps.computeIfAbsent(name, k -> {
            try {

                return mapWireConnectionHub.acquireMap(name, () -> of(byte[]
                        .class, byte[].class)
                        .averageValueSize(maxValueSize)
                        .averageKeySize(maxKeySize)
                        .putReturnsNull(putReturnsNull)
                        .removeReturnsNull(removeReturnsNull)
                        .entries(entries).instance());
            } catch (IOException ioe) {
                throw Jvm.rethrow(ioe);
            }
        });

        if (kClass == byte[].class && vClass == byte[].class)
            return (ChronicleMap<K, V>) underlyingMap;

        final ChronicleMap result = maps.computeIfAbsent(
                name, k -> {
                    try {

                        return new EngineMap<>(
                                underlyingMap,
                                kClass,
                                vClass,
                                TextWire.class);
                    } catch (IOException ioe) {
                        throw Jvm.rethrow(ioe);
                    }
                });

        validateClasses(result, kClass, vClass);

        return (ChronicleMap<K, V>) result;
    }

    @Override
    public FilePerKeyMap getFilePerKeyMap(String name) {
        return fpMaps.computeIfAbsent(name,
                k -> {
                    try {
                        return new FilePerKeyMap(k);
                    } catch (IOException e) {
                        Jvm.rethrow(e);
                        return null;
                    }
                });
    }

    @Override
    public <I> I getService(Class<I> iClass, String name, Class... args) throws IOException {
        //  if (iClass == Chronicle.class)
        //    return (I) getQueue(name);
        if (iClass == ChronicleSet.class)
            return (I) getSet(name, args[0]);
        if (iClass == ChronicleMap.class)
            return (I) getMap(name, args[0], args[1]);
        throw new UnsupportedOperationException();
    }

    @Override
    public <K, V> Subscription<K, MapEventListener<K, V>> createMapSubscription(String name, Class<K> kClass, Class<V> vClass) {
        return createSubscription(name, kClass, (Class<MapEventListener<K, V>>) (Class) MapEventListener.class);
    }

    public <K, V> Subscription<K, MapEventListener<K, V>> createFilePerKeyMapMapSubscription(String name) {
        //Find the corresponding map
        FilePerKeyMap filePerKeyMap = fpMaps.get(name);
        return new FilePerKeyMapSubscription(filePerKeyMap);
    }

    private <K, V> void validateClasses(Map map, Class<K> kClass, Class<V> vClass) {
        // TODO runtime check the key and values classes match
    }

    public void setSet(String name, ChronicleSet set) {
        sets.put(name, set);
    }

    @Override
    public <E> ChronicleSet<E> getSet(String name, Class<E> eClass) {
        ChronicleSet set = sets.get(name);
        if (set != null)
            validateClasses(set, eClass);
        return set;
    }

    private <E> void validateClasses(ChronicleSet set, Class<E> eClass) {
        // TODO runtime check the element class matches.
    }

    public void setThreadPool(String name, ChronicleThreadPool threadPool) {
        threadPools.put(name, threadPool);
    }

    @Override
    public ChronicleThreadPool getThreadPool(String name) {
        return threadPools.get(name);
    }

    public void setCluster(String name, ChronicleCluster cluster) {
        clusters.put(name, cluster);
    }

    @Override
    public ChronicleCluster getCluster(String name) {
        return clusters.get(name);
    }

    @Override
    public Logger getLogger(String name) {
        return Logger.getLogger(name);
    }

    @Override
    public void close() throws IOException {
        mapWireConnectionHub.close();
        fpMaps.values().forEach(FilePerKeyMap::close);
        maps.values().forEach(ChronicleMap::close);
        chronStringMap.values().forEach(ChronicleMap::close);
    }

    @Override
    public <K, V> ChronicleMap<K, V> getChronicleMap(String name, Class<K> kClass, Class<V> vClass) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <K, C> Subscription<K, C> createSubscription(String name, Class<K> kClass, Class<C> callbackClass) {
        throw new UnsupportedOperationException();
    }
}
