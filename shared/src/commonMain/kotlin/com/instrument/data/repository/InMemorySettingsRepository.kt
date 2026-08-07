package com.instrument.data.repository

import com.instrument.domain.model.AppSettings
import com.instrument.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// メモリ内で設定を保持するリポジトリ実装。アプリ再起動時はデフォルト値にリセットされる
class InMemorySettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(settings: AppSettings) {
        _settings.value = settings
    }
}
