package com.thelastpickle.tlpstress

import java.util.concurrent.TimeUnit

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.ScheduledReporter

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.HTTPServer

class Metrics(metricRegistry: MetricRegistry, val reporters: List<ScheduledReporter>) {

    val server: HTTPServer

    fun startReporting() {
        for(reporter in reporters)
            reporter.start(3, TimeUnit.SECONDS)
    }

    init {
        CollectorRegistry.defaultRegistry.register(DropwizardExports(metricRegistry))
        server = HTTPServer(9500)

    }


    val errors = metricRegistry.meter("errors")
    val mutations = metricRegistry.timer("mutations")
    val selects = metricRegistry.timer("selects")

}