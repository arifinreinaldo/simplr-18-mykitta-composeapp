package com.simplr.mykitta2.core

import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.error.ErrorMapper
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ErrorMapperTest {
    @Test
    fun ioExceptionMapsToNetwork() {
        assertEquals(AppError.Network, ErrorMapper.from(IOException("offline")))
    }

    @Test
    fun serializationExceptionMapsToParse() {
        val parsed = ErrorMapper.from(SerializationException("bad json"))
        assertIs<AppError.Parse>(parsed)
    }

    @Test
    fun unknownExceptionMapsToUnknown() {
        val mapped = ErrorMapper.from(IllegalStateException("whoops"))
        assertIs<AppError.Unknown>(mapped)
    }

    @Test
    fun networkMessageIsHumanReadable() {
        val msg = ErrorMapper.message(AppError.Network)
        assertEquals("Network unavailable. Check your connection and try again.", msg)
    }

    @Test
    fun http4xxMessageMentionsStatus() {
        val msg = ErrorMapper.message(AppError.Http(422, null))
        assertEquals("Request rejected (422).", msg)
    }
}
