package com.neko.neuecode.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalSessionErrorsTest {

    @Test
    fun live10013Copy_isExpired() {
        assertTrue(
            PersonalSessionErrors.isExpired(
                code = "10013",
                message = "登录信息已失效，请重新登录",
            ),
        )
    }

    @Test
    fun expiredCopyWithoutCode_isExpired() {
        assertTrue(PersonalSessionErrors.isExpired(code = null, message = "登录已过期，请重新登录"))
        assertTrue(PersonalSessionErrors.isExpired(code = null, message = "请先登录"))
        assertTrue(PersonalSessionErrors.isExpired(code = null, message = "会话无效"))
    }

    @Test
    fun httpAuthFailures_areExpired() {
        assertTrue(PersonalSessionErrors.isExpired(code = "401", message = "Unauthorized"))
        assertTrue(PersonalSessionErrors.isExpired(code = null, message = "Failed HTTP 403"))
    }

    @Test
    fun ordinaryBusinessFailures_areNotExpired() {
        assertFalse(PersonalSessionErrors.isExpired(code = "0", message = "操作成功"))
        assertFalse(PersonalSessionErrors.isExpired(code = "500", message = "json_decode(): Argument #1 (\$json) must be of type string, null given"))
        assertFalse(PersonalSessionErrors.isExpired(code = null, message = "未找到有效余额数据"))
    }

    @Test
    fun needSms_isRecognizedWithoutTreatingAsPlainNetworkError() {
        assertTrue(PersonalSessionErrors.isNeedSms("需要短信验证码，请在登录页完成验证"))
        assertTrue(
            PersonalSessionErrors.isNeedSms(
                AuthRepository.NeedSmsVerificationException(
                    AuthRepository.SmsVerificationRequired(
                        username = "u",
                        password = "p",
                        tempToken = "t",
                        imei = "i",
                        rememberUsername = true,
                        longTermLogin = true,
                    ),
                ),
            ),
        )
        assertFalse(PersonalSessionErrors.isNeedSms("未找到有效余额数据"))
    }

    @Test
    fun live10013_forcesReloginOnce() {
        val action = PersonalSessionErrors.retryAction(
            code = "10013",
            message = "登录信息已失效，请重新登录",
            error = Exception("登录信息已失效，请重新登录"),
            alreadyRefreshed = false,
        )
        assertEquals(PersonalSessionErrors.RetryAction.ReloginAndRetry, action)
        assertEquals(
            PersonalSessionErrors.RetryAction.Fail,
            PersonalSessionErrors.retryAction(
                code = "10013",
                message = "登录信息已失效，请重新登录",
                error = Exception("登录信息已失效，请重新登录"),
                alreadyRefreshed = true,
            ),
        )
    }

    @Test
    fun loginCode3_stopsWithoutReloginLoop() {
        val sms = AuthRepository.NeedSmsVerificationException(
            AuthRepository.SmsVerificationRequired(
                username = "u",
                password = "p",
                tempToken = "t",
                imei = "i",
                rememberUsername = true,
                longTermLogin = true,
            ),
        )
        assertEquals(
            PersonalSessionErrors.RetryAction.StopForSms,
            PersonalSessionErrors.retryAction(
                code = null,
                message = "需要短信验证码，请在登录页完成验证",
                error = sms,
                alreadyRefreshed = false,
            ),
        )
    }
}
