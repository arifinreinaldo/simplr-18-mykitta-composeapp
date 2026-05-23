package com.simplr.mykitta2.core

import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.core.result.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OutcomeTest {
    @Test
    fun successMapTransformsValue() {
        val mapped = Outcome.Success(2).map { it * 3 }
        assertEquals(Outcome.Success(6), mapped)
    }

    @Test
    fun failureMapPreservesError() {
        val original: Outcome<Int> = Outcome.Failure(AppError.Network)
        val mapped = original.map { it + 1 }
        assertIs<Outcome.Failure>(mapped)
        assertEquals(AppError.Network, mapped.error)
    }

    @Test
    fun idleAndLoadingPassThrough() {
        assertEquals(Outcome.Idle, (Outcome.Idle as Outcome<Int>).map { it + 1 })
        assertEquals(Outcome.Loading, (Outcome.Loading as Outcome<Int>).map { it + 1 })
    }
}
