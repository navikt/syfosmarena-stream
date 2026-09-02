package no.nav.syfo

import io.prometheus.client.hotspot.DefaultExports
import java.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.JournaledReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmelding
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

private val jsonMapper: JsonMapper = jacksonMapperBuilder().build()

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmarena-stream")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    startKafkaAivenStream(env, applicationState)
    applicationServer.start()
}

fun startKafkaAivenStream(env: Environment, applicationState: ApplicationState) {
    val streamsBuilder = StreamsBuilder()
    val streamProperties =
        KafkaUtils.getAivenKafkaConfig("sykmleding-stream")
            .toStreamsConfig(env.applicationName, Serdes.String()::class, Serdes.String()::class)
    streamProperties[StreamsConfig.APPLICATION_ID_CONFIG] = env.applicationId
    val inputStream =
        streamsBuilder
            .stream(
                listOf(env.okSykmeldingTopic, env.manuellSykmeldingTopic),
                Consumed.with(Serdes.String(), Serdes.String()),
            )
            .filter { _, value -> skalBehandles(value) }

    val journalOpprettetStream =
        streamsBuilder.stream(
            listOf(env.journalOpprettetTopic),
            Consumed.with(Serdes.String(), Serdes.String()),
        )

    val joinWindow = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofDays(14))

    inputStream
        .join(
            journalOpprettetStream,
            { sm2013, journalKafkaMessage ->
                log.info("streamed to Aiven")
                jsonMapper.writeValueAsString(
                    JournaledReceivedSykmelding(
                        receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                        journalpostId =
                            jsonMapper
                                .readValue<JournalKafkaMessage>(journalKafkaMessage)
                                .journalpostId,
                    )
                )
            },
            joinWindow,
        )
        .to(env.privatArenaInputTopic)

    val stream = KafkaStreams(streamsBuilder.build(), streamProperties)
    stream.setUncaughtExceptionHandler { err ->
        log.error("Aiven: Caught exception in stream: ${err.message}", err)
        stream.close(Duration.ofSeconds(30))
        applicationState.ready = false
        applicationState.alive = false
        throw err
    }

    stream.setStateListener { newState, oldState ->
        log.info("Aiven: From state={} to state={}", oldState, newState)
        if (newState == KafkaStreams.State.ERROR) {
            // if the stream has died there is no reason to keep spinning
            log.error("Aiven: Closing stream because it went into error state")
            stream.close(Duration.ofSeconds(30))
            log.error("Aiven: Restarter applikasjonen")
            applicationState.ready = false
            applicationState.alive = false
        }
    }
    stream.start()
}

fun skalBehandles(value: String?): Boolean {
    val receivedSykmelding = value?.let { jsonMapper.readValue<ReceivedSykmelding?>(it) }
    if (receivedSykmelding == null) {
        log.info("Mottok melding uten sykmelding, filtrerer den bort")
        return false
    }
    return receivedSykmelding.skalBehandles()
}

fun ReceivedSykmelding.skalBehandles(): Boolean {
    return merknader?.any { it.type == "UNDER_BEHANDLING" } != true && utenlandskSykmelding == null
}
