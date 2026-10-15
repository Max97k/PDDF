package com.max97k.pddf.domain.usecase

import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.Assert.assertNotNull

class ExportMetadataReportUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val useCase = ExportMetadataReportUseCase(testDispatcher)

    @Test
    fun compilesAndInstantiates() {
        assertNotNull(useCase)
    }
}
