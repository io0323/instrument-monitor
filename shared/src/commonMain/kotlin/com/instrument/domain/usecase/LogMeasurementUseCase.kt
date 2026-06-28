package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.GpsRepository
import com.instrument.domain.repository.LogRepository

class LogMeasurementUseCase(
    private val logRepo: LogRepository,
    private val gpsRepo: GpsRepository
) {
    suspend operator fun invoke(status: GasStatus, manualSave: Boolean = false): Result<Unit> {
        val shouldSave = status.level >= GasLevel.DANGER || manualSave
        if (!shouldSave) return Result.success(Unit)
        return runCatching {
            val location = runCatching {
                var loc: Pair<Double, Double>? = null
                gpsRepo.observeLocation().collect { loc = it; return@collect }
                loc ?: Pair(0.0, 0.0)
            }.getOrDefault(Pair(0.0, 0.0))
            logRepo.save(
                GeoTaggedReading(
                    reading = status.reading,
                    lat     = location.first,
                    lng     = location.second,
                    level   = status.level
                )
            )
        }
    }
}
