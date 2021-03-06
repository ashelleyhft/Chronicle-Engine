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
package net.openhft.chronicle.engine.server;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.engine.client.internal.ChronicleEngine;
import net.openhft.chronicle.engine.server.internal.EngineWireHandler;
import net.openhft.chronicle.map.MapWireConnectionHub;
import net.openhft.chronicle.network.AcceptorEventHandler;
import net.openhft.chronicle.network.event.EventGroup;
import net.openhft.chronicle.wire.Wire;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Rob Austin
 */
public class ServerEndpoint implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ServerEndpoint.class);
    private final byte localIdentifier;
    private final Function<Bytes, Wire> byteToWire;
    private EventGroup eg = new EventGroup();

    private AcceptorEventHandler eah;
    private MapWireConnectionHub mapWireConnectionHub;
    private ChronicleEngine chronicleEngine;

    public ServerEndpoint(byte localIdentifier,
                          @NotNull final ChronicleEngine chronicleEngine,
                          @NotNull final Class<? extends Wire> wireClass) throws IOException {
        this(0, localIdentifier, chronicleEngine, wireClass);
    }

    public ServerEndpoint(int port,
                          byte localIdentifier,
                          @NotNull final ChronicleEngine chronicleEngine,
                          @NotNull final Class<? extends Wire> wireClass) throws IOException {
        this.localIdentifier = localIdentifier;
        this.chronicleEngine = chronicleEngine;

        this.byteToWire = Wire.bytesToWire(wireClass);

        start(port);
    }

    public AcceptorEventHandler start(int port) throws IOException {
        eg.start();

        AcceptorEventHandler eah = new AcceptorEventHandler(port, () -> {

            final Map<Long, String> cidToCsp = new HashMap<>();

            try {
                mapWireConnectionHub = new MapWireConnectionHub(localIdentifier, 8085);

                return new EngineWireHandler(cidToCsp, chronicleEngine, byteToWire);
            } catch (IOException e) {
                LOG.error("", e);
            }
            return null;
        });

        eg.addHandler(eah);
        this.eah = eah;
        return eah;
    }

    public int getPort() throws IOException {
        return eah.getLocalPort();
    }

    public void stop() {
        eg.stop();
    }

    @Override
    public void close() throws IOException {
        stop();
        eg.close();
        eah.close();
        if (mapWireConnectionHub != null)
            mapWireConnectionHub.close();
        chronicleEngine.close();
    }
}
