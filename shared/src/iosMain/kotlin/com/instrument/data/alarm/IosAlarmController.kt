package com.instrument.data.alarm

import com.instrument.domain.model.GasLevel

// iOS向けアラーム実装（Phase 8 で AudioServicesPlayAlertSound に置き換え）
class IosAlarmController : AlarmController {
    override fun trigger(level: GasLevel) { /* iOS実装は Phase 8 で追加 */ }
    override fun dismiss() {}
    override fun release() {}
}
