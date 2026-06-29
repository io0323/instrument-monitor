package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.SensorReading
import com.instrument.domain.model.Trend
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan

class MonitorGasUseCase(private val repo: BleRepository) {

    operator fun invoke(): Flow<GasStatus> = repo.observeSensorData()
        .scan(Pair(ArrayDeque<Float>(), null as GasStatus?)) { (deque, _), reading ->
            if (deque.size >= 5) deque.removeFirst()
            deque.addLast(reading.ppm)
            val trend = if (deque.size < 5) Trend.STABLE else {
                val diff = deque.last() - deque.first()
                when {
                    diff > +10f -> Trend.RISING
                    diff < -10f -> Trend.FALLING
                    else        -> Trend.STABLE
                }
            }
            Pair(deque, GasStatus(reading, reading.toGasLevel(), trend))
        }
        .mapNotNull { it.second }
}

private fun SensorReading.toGasLevel(): GasLevel = when {
    ppm < 50f   -> GasLevel.SAFE
    ppm < 200f  -> GasLevel.WARNING
    ppm < 350f  -> GasLevel.DANGER
    else        -> GasLevel.CRITICAL
}
