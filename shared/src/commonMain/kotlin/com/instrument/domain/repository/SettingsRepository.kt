package com.instrument.domain.repository

import com.instrument.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// アプリ設定の読み書きを担うリポジトリインターフェース
interface SettingsRepository {
    /** 現在の設定を購読可能な StateFlow として公開する */
    val settings: StateFlow<AppSettings>

    /** 設定を更新する */
    fun update(settings: AppSettings)

    companion object {
        /**
         * DI なしで利用できるデフォルト (インメモリ) 実装を返す。
         * テストや UseCase のデフォルトパラメータとして使用する。
         */
        fun default(): SettingsRepository = object : SettingsRepository {
            private val _settings = MutableStateFlow(AppSettings())
            override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
            override fun update(settings: AppSettings) { _settings.value = settings }
        }
    }
}
