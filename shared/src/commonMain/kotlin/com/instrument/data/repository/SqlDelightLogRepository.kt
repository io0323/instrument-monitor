package com.instrument.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.instrument.data.db.DatabaseDriverFactory
import com.instrument.db.InstrumentDatabase
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// SQLDelight を使った計測ログ永続化実装
class SqlDelightLogRepository(driverFactory: DatabaseDriverFactory) : LogRepository {

    private val db = InstrumentDatabase(driverFactory.createDriver())

    override suspend fun save(reading: GeoTaggedReading): Result<Long> = runCatching {
        db.sensorReadingsQueries.insert(
            ppm         = reading.reading.ppm.toDouble(),
            temperature = reading.reading.temperature.toDouble(),
            humidity    = reading.reading.humidity.toDouble(),
            lat         = reading.lat,
            lng         = reading.lng,
            gas_level   = reading.level.name,
            timestamp   = reading.reading.timestamp,
        )
        db.sensorReadingsQueries.selectAll().executeAsList().lastOrNull()?.id ?: 0L
    }

    override fun getAllReadings(): Flow<List<GeoTaggedReading>> =
        db.sensorReadingsQueries.selectAll().asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toGeoTaggedReading() } }

    override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> =
        db.sensorReadingsQueries.selectDangerous().asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toGeoTaggedReading() } }

    override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = runCatching {
        db.sensorReadingsQueries.deleteOlderThan(epochMs)
    }

    override suspend fun exportCsv(): Result<String> = runCatching {
        val rows = db.sensorReadingsQueries.selectAll().executeAsList()
        buildString {
            appendLine("timestamp,ppm,temperature,humidity,lat,lng,level")
            rows.forEach { row ->
                appendLine("${row.timestamp},${row.ppm},${row.temperature},${row.humidity},${row.lat},${row.lng},${row.gas_level}")
            }
        }
    }
}

private fun com.instrument.db.Sensor_readings.toGeoTaggedReading() = GeoTaggedReading(
    id      = id,
    reading = SensorReading(ppm.toFloat(), temperature.toFloat(), humidity.toFloat(), timestamp),
    lat     = lat,
    lng     = lng,
    level   = GasLevel.valueOf(gas_level),
)
