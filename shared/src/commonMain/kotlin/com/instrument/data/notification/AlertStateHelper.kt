package com.instrument.data.notification

import com.instrument.domain.model.GasLevel

/**
 * バックグラウンド通知のアラート発火・消去を判定する純粋関数群。
 *
 * Service / ViewModel のいずれからも参照でき、
 * Android 依存なしでユニットテスト可能にするために commonMain に配置する。
 */

/**
 * アラート通知を新たに発火すべきか判定する。
 *
 * 条件:
 * - 現在レベルが DANGER 以上
 * - かつ、直前に発火したレベルと異なる (同一レベルの連続通知を防ぐ)
 *
 * @param current  最新の [GasLevel]
 * @param previous 前回発火時の [GasLevel]。未発火の場合は null
 */
fun shouldFireAlert(current: GasLevel, previous: GasLevel?): Boolean =
    current >= GasLevel.DANGER && current != previous

/**
 * 既存のアラート通知を消去すべきか判定する。
 *
 * 条件:
 * - 現在レベルが DANGER 未満 (回復)
 * - かつ、発火済みのアラートが存在する
 *
 * @param current  最新の [GasLevel]
 * @param previous 前回発火時の [GasLevel]。未発火の場合は null
 */
fun shouldCancelAlert(current: GasLevel, previous: GasLevel?): Boolean =
    current < GasLevel.DANGER && previous != null
