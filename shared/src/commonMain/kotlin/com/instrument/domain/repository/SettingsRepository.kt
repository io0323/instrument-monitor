package com.instrument.domain.repository

import com.instrument.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

// アプリ設定の読み書きを担うリポジトリインターフェース
interface SettingsRepository {
    /** 現在の設定を購読可能な StateFlow として公開する */
    val settings: StateFlow<AppSettings>

    /** 設定を更新する */
    fun update(settings: AppSettings)
}
