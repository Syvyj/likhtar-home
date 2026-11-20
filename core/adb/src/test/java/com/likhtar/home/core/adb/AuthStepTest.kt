package com.likhtar.home.core.adb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Що робити з черговим AUTH-токеном adbd. Тихий виклик не має показувати «Дозволити налагодження?». */
class AuthStepTest {
    @Test fun `перший токен — підписати`() = assertEquals(AuthStep.SIGN, authStep(signed = false, offeredKey = false, offerKey = true))

    @Test fun `підпис не прийняли — запропонувати ключ, якщо можна`() =
        assertEquals(AuthStep.OFFER_KEY, authStep(signed = true, offeredKey = false, offerKey = true))

    @Test fun `тихий виклик ключа не пропонує`() =
        assertEquals(AuthStep.NOT_APPROVED, authStep(signed = true, offeredKey = false, offerKey = false))

    @Test fun `ключ запропонували й відмовили — відмова`() =
        assertEquals(AuthStep.REJECTED, authStep(signed = true, offeredKey = true, offerKey = true))
}
