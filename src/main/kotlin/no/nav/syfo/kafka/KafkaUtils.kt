package no.nav.syfo.kafka

import java.util.Properties
import kotlin.reflect.KClass
import org.apache.kafka.common.serialization.*
import org.apache.kafka.streams.StreamsConfig

fun Properties.toStreamsConfig(
    applicationName: String,
    valueSerde: KClass<out Serde<out Any>>,
    keySerde: KClass<out Serde<out Any>> = Serdes.String()::class
): Properties =
    Properties().also {
        it.putAll(this)
        // TODO hacky workaround for kafka streams issues
        it.setProperty("acks", "1")
        it.remove("enable.idempotence")
        it[StreamsConfig.APPLICATION_ID_CONFIG] = applicationName
        it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = keySerde.java
        it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = valueSerde.java
    }
