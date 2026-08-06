package com.instrument.presentation.ui.devicelist

import kotlin.test.Test
import kotlin.test.assertEquals

// DeviceListScreen の純粋ロジック関数を検証するテスト
class DeviceListScreenLogicTest {

    @Test
    fun deviceListTitle_スキャン中はスキャン中を返す() {
        assertEquals("スキャン中...", deviceListTitle(isScanning = true))
    }

    @Test
    fun deviceListTitle_スキャン停止中はデバイス一覧を返す() {
        assertEquals("デバイス一覧", deviceListTitle(isScanning = false))
    }
}
