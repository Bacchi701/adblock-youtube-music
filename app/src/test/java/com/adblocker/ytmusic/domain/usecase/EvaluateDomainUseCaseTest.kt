package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.domain.model.BlocklistRules
import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.model.FilterDecision
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateDomainUseCaseTest {

    private class FakeRepository(
        private val rules: BlocklistRules,
    ) : BlocklistRepository {
        var loadCount = 0
            private set

        override suspend fun loadRules(): BlocklistRules {
            loadCount++
            return rules
        }

        override suspend fun sources(): List<BlocklistSource> = emptyList()
    }

    @Test
    fun `sin preparar deja pasar todo`() {
        val useCase = EvaluateDomainUseCase(FakeRepository(BlocklistRules(setOf("ads.com"))))
        assertEquals(FilterDecision.Allow, useCase("ads.com"))
    }

    @Test
    fun `tras preparar aplica bloqueos y excepciones`() = runTest {
        val useCase = EvaluateDomainUseCase(
            FakeRepository(BlocklistRules(setOf("ads.com"), setOf("ok.ads.com"))),
        )
        useCase.prepare()

        assertTrue(useCase("x.ads.com").isBlocked)
        assertEquals(FilterDecision.Allow, useCase("ok.ads.com"))
    }

    @Test
    fun `el motor se carga una sola vez`() = runTest {
        val repository = FakeRepository(BlocklistRules(setOf("ads.com")))
        val useCase = EvaluateDomainUseCase(repository)

        useCase.prepare()
        useCase.prepare()

        assertEquals(1, repository.loadCount)
    }
}
