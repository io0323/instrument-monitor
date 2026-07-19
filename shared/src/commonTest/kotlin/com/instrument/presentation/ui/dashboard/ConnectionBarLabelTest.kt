package com.instrument.presentation.ui.dashboard

import com.instrument.domain.repository.BleConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals

// ConnectionBar の connectionBarLabel 純粋ロジックを検証するテスト
class ConnectionBarLabelTest {

    @Test
    fun Connected_デバイス名ありはデバイス名を返す() {
        assertEquals(
            "instrument-G100",
            connectionBarLabel(BleConnectionState.Connected, "instrument-G100"),
        )
    }

    @Test
    fun Connected_デバイス名なしは接続済みを返す() {
        assertEquals(
            "接続済み",
            connectionBarLabel(BleConnectionState.Connected, null),
        )
    }

    @Test
    fun Connecting_は接続中を返す() {
        assertEquals(
            "接続中...",
            connectionBarLabel(BleConnectionState.Connecting, null),
        )
    }

    @Test
    fun Scanning_は接続中を返す() {
        assertEquals(
            "接続中...",
            connectionBarLabel(BleConnectionState.Scanning, null),
        )
    }

    @Test
    fun Error_はエラーメッセージを返す() {
        assertEquals(
            "タイムアウト",
            connectionBarLabel(BleConnectionState.Error("タイムアウト"), null),
        )
    }

    @Test
    fun Disconnected_は未接続を返す() {
        assertEquals(
            "未接続",
            connectionBarLabel(BleConnectionState.Disconnected, null),
        )
    }
}

