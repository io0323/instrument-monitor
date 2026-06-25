package com.instrument.domain.repository

import kotlinx.coroutines.flow.Flow

// GPS位置情報の抽象化インターフェース
// Phase 6 で実装: AndroidGpsRepository / IosGpsRepository
interface GpsRepository {
    // 現在位置を Flow で返す (緯度, 経度)
    fun observeLocation(): Flow<Pair<Double, Double>>
}
