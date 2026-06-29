package com.instrument.data.alarm

import com.instrument.domain.model.GasLevel

// アラーム制御インターフェース（音声・振動の抽象化）
interface AlarmController {
    fun trigger(level: GasLevel)
    fun dismiss()
    fun release()
}
