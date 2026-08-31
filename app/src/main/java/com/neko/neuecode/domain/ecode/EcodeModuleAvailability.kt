package com.neko.neuecode.domain.ecode

/**
 * Temporary kill-switch for eCode / 一号通 pay-code, recharge, and related
 * background work. Schedule and campus VPN stay available.
 */
object EcodeModuleAvailability {
    const val ENABLED = false

    const val PAUSE_NOTICE =
        "学校教务系统近期出现高频次更新，此模块需等待教务系统更新完毕后，再逐步开放。带来各种困扰，敬请谅解！"

    fun shouldFetchPayCode(): Boolean = ENABLED
    fun shouldScheduleEcodeBackgroundWork(): Boolean = ENABLED
    fun shouldOpenPayCodeWebView(): Boolean = ENABLED
    fun shouldOpenRecharge(): Boolean = ENABLED
    fun shouldKeepSchedule(): Boolean = true
    fun shouldKeepCampusVpn(): Boolean = true

    fun defaultStartRoute(): String = "schedule"
}
