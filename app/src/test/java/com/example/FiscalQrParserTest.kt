package com.example

import com.example.service.receipt.FiscalQrParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FiscalQrParserTest {

    @Test
    fun parse_validStandardQr_success() {
        val qr = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"
        val result = FiscalQrParser.parse(qr)

        assertNotNull(result)
        assertEquals("9287440300647312", result!!.fn)
        assertEquals("34873", result.fd)
        assertEquals("3526148825", result.fp)
        assertEquals(1247.00, result.sum!!, 0.001)
        assertEquals(1, result.n)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2020, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(49, cal.get(Calendar.MINUTE))
    }

    @Test
    fun parse_validQrWithFd_success() {
        val qr = "t=20230512T175900&s=89.50&fn=9999078900012345&fd=9876&fp=123456789&n=2"
        val result = FiscalQrParser.parse(qr)

        assertNotNull(result)
        assertEquals("9999078900012345", result!!.fn)
        assertEquals("9876", result.fd)
        assertEquals("123456789", result.fp)
        assertEquals(89.50, result.sum!!, 0.001)
        assertEquals(2, result.n)
    }

    @Test
    fun parse_urlWrappedQr_success() {
        val url = "https://check.nalog.ru/rec/v1?t=20210315T1200&s=500.00&fn=1111222233334444&i=555&fp=777888&n=1"
        val result = FiscalQrParser.parse(url)

        assertNotNull(result)
        assertEquals("1111222233334444", result!!.fn)
        assertEquals("555", result.fd)
        assertEquals("777888", result.fp)
        assertEquals(500.0, result.sum!!, 0.001)
    }

    @Test
    fun parse_missingMandatoryField_returnsNull() {
        // Missing fp
        val noFp = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873"
        assertNull(FiscalQrParser.parse(noFp))

        // Missing fn
        val noFn = "t=20201014T1849&s=1247.00&i=34873&fp=3526148825"
        assertNull(FiscalQrParser.parse(noFn))

        // Missing fd/i
        val noFd = "t=20201014T1849&s=1247.00&fn=9287440300647312&fp=3526148825"
        assertNull(FiscalQrParser.parse(noFd))
    }

    @Test
    fun parse_nonFiscalString_returnsNull() {
        assertNull(FiscalQrParser.parse("https://google.com"))
        assertNull(FiscalQrParser.parse(""))
        assertNull(FiscalQrParser.parse("WIFI:S:MyNetwork;T:WPA;P:MyPassword;;"))
        assertNull(FiscalQrParser.parse(null))
    }

    @Test
    fun operationTypeToString_handlesAllCodes() {
        assertEquals("Приход", FiscalQrParser.operationTypeToString(1))
        assertEquals("Возврат прихода", FiscalQrParser.operationTypeToString(2))
        assertEquals("Расход", FiscalQrParser.operationTypeToString(3))
        assertEquals("Возврат расхода", FiscalQrParser.operationTypeToString(4))
        assertEquals("Приход", FiscalQrParser.operationTypeToString(null))
        assertEquals("Приход", FiscalQrParser.operationTypeToString(99))
    }
}
