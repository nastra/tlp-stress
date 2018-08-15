package com.thelastpickle.tlpstress

import com.codahale.metrics.*
import com.codahale.metrics.Timer
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log
import com.github.ajalt.mordant.TermColors

class SingleLineConsoleReporter(registry: MetricRegistry) : ScheduledReporter(registry,
    "single-line-console-reporter",
    MetricFilter.ALL,
    TimeUnit.SECONDS,
    TimeUnit.MILLISECONDS
    ) {

    var lines = 0L

    var width = mutableMapOf<Int, Int>( ).withDefault { 0 }


    var opHeaders = listOf("Count", "Latency (p99)", "5min (req/s)")

    val formatter = DecimalFormat("##.##")

    val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    val termColors = TermColors()

    override fun report(gauges: SortedMap<String, Gauge<Any>>?,
                        counters: SortedMap<String, Counter>?,
                        histograms: SortedMap<String, Histogram>?,
                        meters: SortedMap<String, Meter>?,
                        timers: SortedMap<String, Timer>?) {


        if(lines % 10L == 0L)
            printHeader()

        val state = AtomicInteger()

        // this is a little weird, but we should show the same headers for writes & selects
        val queries = listOf(timers!!["mutations"]!!, timers!!["selects"]!!)

        for((i, queryType) in queries.withIndex()) {
            with(queryType) {
                printColumn(count, state.getAndIncrement())

                val duration = convertDuration(snapshot.get99thPercentile())

                printColumn(duration, state.getAndIncrement())
                printColumn(formatter.format(fiveMinuteRate), state.getAndIncrement())

            }
            if(i == 0) {
                print(" | ")
            }
        }
        println()
        lines++
    }

    fun printColumn(value: Double, index: Int) {
        // round to 2 decimal places
        val tmp = DecimalFormat("##.##").format(value)

        printColumn(tmp, index)
    }

    fun printColumn(value: Long, index: Int) {
        printColumn(value.toString(), index)
    }

    fun printColumn(value: Int, index: Int) {
        printColumn(value.toString(), index)
    }

    fun printColumn(value: String, index: Int) {
        val width = getWidth(index, value)
        val tmp = value.padStart(width)
        print(tmp)
    }

    fun printHeader() {

        println("Writes")

        var i = 0

        for(x in 0..1) {

            for (h in opHeaders) {

                val colWidth = getWidth(i, h)
                val required = colWidth - h.length

//                val tmp = h.padStart(colWidth)
//                val tmp = "\\e[4m-$h-\\e[0"

                val tmp = " ".repeat(required) + termColors.underline(h)



                print(tmp)
                i++
            }
            if(x == 0) {
                print(" | ")
            }

        }

        println()

        // errors


    }

    /**
     * Gets the width for a column, resizing the column if necessary
     */
    fun getWidth(i: Int, value : String = "") : Int {
        val tmp = width.getValue(i)
        if(value.length > tmp) {

            logger.debug("Resizing column[$i] to ${value.length}")
            // give a little extra padding in case the number grows quickly
            width.set(i, value.length + 2)
        }
        return width.getValue(i)
    }



}
