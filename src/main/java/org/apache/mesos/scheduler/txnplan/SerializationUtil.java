package org.apache.mesos.scheduler.txnplan;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Output;
import de.javakaffee.kryoserializers.*;
import de.javakaffee.kryoserializers.protobuf.ProtobufSerializer;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * By default, we'll use pooled serializers, and we'll automatically support Protobufs,
 * Dates, and URIs, as well as the full standard Kryo experience (so everything should
 * work).
 */
public class SerializationUtil {
    public static final ThreadLocal<Kryo> kryos = new ThreadLocal<Kryo>() {
        protected Kryo initialValue() {
            Class arrayAsListClazz = Arrays.asList("").getClass();
            Kryo kryo = new Kryo() {
                @Override
                public Serializer<?> getDefaultSerializer(Class clazz) {
                    if (com.google.protobuf.GeneratedMessage.class.isAssignableFrom(clazz)) {
                        return new ProtobufSerializer();
                    }
                    if (UUID.class.isAssignableFrom(clazz)) {
                        return new UUIDSerializer();
                    }
                    if (Date.class.isAssignableFrom(clazz)) {
                        return new DateSerializer(clazz);
                    }
                    if (URI.class.isAssignableFrom(clazz)) {
                        return new URISerializer();
                    }
                    if (arrayAsListClazz.isAssignableFrom(clazz)) {
                        return new ArraysAsListSerializer();
                    }
                    return super.getDefaultSerializer(clazz);
                }
            };
            UnmodifiableCollectionsSerializer.registerSerializers(kryo);
            return kryo;
        };
    };
}
