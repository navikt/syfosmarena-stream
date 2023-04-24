package no.nav.syfo.application.metrics

import io.prometheus.client.Histogram

const val METRICS_NS = "syfosmarena_stream"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .namespace(METRICS_NS)
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()
