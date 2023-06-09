package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmarena-stream"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val manuellSykmeldingTopic: String = "teamsykmelding.manuell-behandling-sykmelding",
    val journalOpprettetTopic: String = "teamsykmelding.oppgave-journal-opprettet",
    val privatArenaInputTopic: String = "teamsykmelding.privat-arena-input",
    val applicationId: String = getEnvVar("KAFKA_STREAMS_APPLICATION_ID"),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
