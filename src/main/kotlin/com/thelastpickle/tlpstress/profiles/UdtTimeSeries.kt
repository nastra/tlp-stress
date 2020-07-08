package com.thelastpickle.tlpstress.profiles

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.type.UserDefinedType
import com.datastax.oss.driver.api.core.uuid.Uuids
import com.thelastpickle.tlpstress.PartitionKey
import com.thelastpickle.tlpstress.StressContext
import com.thelastpickle.tlpstress.WorkloadParameter
import com.thelastpickle.tlpstress.generators.*
import com.thelastpickle.tlpstress.generators.functions.Random


/**
 * Create a simple time series use case with some number of partitions
 * TODO make it use TWCS
 */
class UdtTimeSeries : IStressProfile {

    override fun schema(): List<String> {
        val queryUdt = """CREATE TYPE IF NOT EXISTS sensor_data_details (
                          data1 text,
                          data2 text,
                          data3 text
                        )""".trimIndent()

        val queryTable = """CREATE TABLE IF NOT EXISTS sensor_data_udt (
                            sensor_id text,
                            timestamp timeuuid,
                            data frozen<sensor_data_details>,
                            primary key(sensor_id, timestamp))
                            WITH CLUSTERING ORDER BY (timestamp DESC)
                           """.trimIndent()

        return listOf(queryUdt, queryTable)
    }

    lateinit var insert: PreparedStatement
    lateinit var getPartitionHead: PreparedStatement
    lateinit var deletePartitionHead: PreparedStatement

    @WorkloadParameter("Limit select to N rows.")
    var limit = 500

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO sensor_data_udt (sensor_id, timestamp, data) VALUES (?, ?, ?)")
        getPartitionHead = session.prepare("SELECT * from sensor_data_udt WHERE sensor_id = ? LIMIT ?")
        deletePartitionHead = session.prepare("DELETE from sensor_data_udt WHERE sensor_id = ?")
    }

    /**
     * need to fix custom arguments
     */
    override fun getRunner(context: StressContext): IStressRunner {

        val dataField = context.registry.getGenerator("sensor_data", "data")

        return object : IStressRunner {

            val udt = context.session.metadata.getKeyspace(context.session.keyspace.get()).get().getUserDefinedType(CqlIdentifier.fromInternal("sensor_data_details")).get()

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound = getPartitionHead.bind(partitionKey.getText(), limit)
                return Operation.SelectStatement(bound)
            }

            override fun getNextMutation(partitionKey: PartitionKey) : Operation {
                val data = dataField.getText()
                val chunks = data.chunked(data.length/3)
                val udtValue = udt.newValue().setString("data1", chunks[0]).setString("data2", chunks[1]).setString("data3", chunks[2])
                val timestamp = Uuids.timeBased()
                val bound = insert.bind(partitionKey.getText(),timestamp, udtValue)
                return Operation.Mutation(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val bound = deletePartitionHead.bind(partitionKey.getText())
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        return mapOf(Field("sensor_data", "data") to Random().apply {min=100; max=200})
    }


}