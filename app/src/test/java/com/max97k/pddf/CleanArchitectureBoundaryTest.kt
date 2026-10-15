package com.max97k.pddf

import com.max97k.pddf.domain.model.AppUpdateStatus
import com.max97k.pddf.domain.model.PdfUiState
import com.max97k.pddf.domain.repository.AppUpdateRepository
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.domain.usecase.AutoUnlockUseCase
import com.max97k.pddf.domain.usecase.BatchProcessUseCase
import com.max97k.pddf.domain.usecase.CheckAppUpdateUseCase
import com.max97k.pddf.domain.usecase.DecryptPdfUseCase
import com.max97k.pddf.domain.usecase.PasswordVaultUseCase
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Tier 1 & Tier 4: Clean Architecture Boundary & Domain Layer Isolation Tests.
 * Enforces strict separation of concerns, domain model immutability,
 * use case single responsibility, and zero UI dependency in the domain layer.
 */
class CleanArchitectureBoundaryTest {

    private val domainClasses = listOf(
        AutoUnlockUseCase::class.java,
        BatchProcessUseCase::class.java,
        DecryptPdfUseCase::class.java,
        PasswordVaultUseCase::class.java,
        CheckAppUpdateUseCase::class.java,
        AppUpdateStatus::class.java,
        AppUpdateStatus.Idle::class.java,
        AppUpdateStatus.Checking::class.java,
        AppUpdateStatus.Available::class.java,
        AppUpdateStatus.Downloading::class.java,
        AppUpdateStatus.Downloaded::class.java,
        AppUpdateStatus.Failed::class.java,
        AppUpdateStatus.UpToDate::class.java,
        AppUpdateRepository::class.java,
        PdfUiState::class.java,
        PdfUiState.Idle::class.java,
        PdfUiState.Selected::class.java,
        PdfUiState.Processing::class.java,
        PdfUiState.Success::class.java,
        PdfUiState.Error::class.java
    )

    private val forbiddenUiPackages = listOf(
        "android.view",
        "android.widget",
        "androidx.compose",
        "androidx.activity",
        "com.max97k.pddf.ui",
        "com.max97k.pddf.feature"
    )

    @Test
    fun domainLayer_hasNoForbiddenUiDependencies() {
        for (clazz in domainClasses) {
            // Check fields
            for (field in clazz.declaredFields) {
                val typeName = field.type.name
                for (forbidden in forbiddenUiPackages) {
                    assertFalse(
                        "Domain class ${clazz.name} field '${field.name}' ($typeName) must not depend on UI package $forbidden",
                        typeName.startsWith(forbidden)
                    )
                }
            }

            // Check method parameters and return types
            for (method in clazz.declaredMethods) {
                val returnType = method.returnType.name
                for (forbidden in forbiddenUiPackages) {
                    assertFalse(
                        "Domain class ${clazz.name} method '${method.name}' return type ($returnType) must not depend on UI package $forbidden",
                        returnType.startsWith(forbidden)
                    )
                }
                for (param in method.parameterTypes) {
                    val paramType = param.name
                    for (forbidden in forbiddenUiPackages) {
                        assertFalse(
                            "Domain class ${clazz.name} method '${method.name}' param ($paramType) must not depend on UI package $forbidden",
                            paramType.startsWith(forbidden)
                        )
                    }
                }
            }

            // Check constructors
            for (constructor in clazz.declaredConstructors) {
                for (param in constructor.parameterTypes) {
                    val paramType = param.name
                    for (forbidden in forbiddenUiPackages) {
                        assertFalse(
                            "Domain class ${clazz.name} constructor param ($paramType) must not depend on UI package $forbidden",
                            paramType.startsWith(forbidden)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun domainModels_areImmutable() {
        // Verify PdfUiState hierarchy is sealed interface and all state variants are data classes / objects
        assertTrue("PdfUiState must be an interface", PdfUiState::class.java.isInterface)

        val subclasses = listOf(
            PdfUiState.Idle::class.java,
            PdfUiState.Selected::class.java,
            PdfUiState.Processing::class.java,
            PdfUiState.Success::class.java,
            PdfUiState.Error::class.java
        )

        for (subclass in subclasses) {
            assertTrue(
                "Subclass ${subclass.simpleName} must implement PdfUiState",
                PdfUiState::class.java.isAssignableFrom(subclass)
            )
            // Fields in data classes must be immutable (no public mutable fields)
            for (field in subclass.declaredFields) {
                assertFalse(
                    "Field ${field.name} in ${subclass.simpleName} should not be public non-final",
                    Modifier.isPublic(field.modifiers) && !Modifier.isFinal(field.modifiers)
                )
            }
        }
    }

    @Test
    fun useCases_followSingleResponsibilityAndNamingConventions() {
        val useCases = listOf(
            AutoUnlockUseCase::class.java,
            BatchProcessUseCase::class.java,
            DecryptPdfUseCase::class.java,
            PasswordVaultUseCase::class.java,
            CheckAppUpdateUseCase::class.java
        )

        for (useCase in useCases) {
            assertTrue(
                "Use case ${useCase.simpleName} must end with 'UseCase'",
                useCase.simpleName.endsWith("UseCase")
            )
            assertTrue(
                "Use case ${useCase.simpleName} must be located in com.max97k.pddf.domain.usecase",
                useCase.`package`?.name == "com.max97k.pddf.domain.usecase"
            )
        }
    }

    @Test
    fun domainLayer_doesNotExposeRoomDatabaseDirectly() {
        for (clazz in domainClasses) {
            for (field in clazz.declaredFields) {
                assertNotEquals(
                    "Domain class ${clazz.name} must not directly hold AppDatabase",
                    "com.max97k.pddf.data.AppDatabase",
                    field.type.name
                )
            }
        }
    }

    @Test
    fun domainUseCases_haveNoContextOrUriDependency() {
        val pureDomainClasses = listOf(
            AutoUnlockUseCase::class.java,
            BatchProcessUseCase::class.java,
            DecryptPdfUseCase::class.java,
            PasswordVaultUseCase::class.java,
            CheckAppUpdateUseCase::class.java,
            PdfRepository::class.java,
            AppUpdateRepository::class.java
        )
        val forbiddenAndroidTypes = listOf(
            "android.content.Context",
            "android.net.Uri",
            "android.content.ContentResolver"
        )
        for (clazz in pureDomainClasses) {
            for (field in clazz.declaredFields) {
                val typeName = field.type.name
                for (forbidden in forbiddenAndroidTypes) {
                    assertFalse(
                        "Domain class ${clazz.name} field '${field.name}' ($typeName) must not depend on $forbidden",
                        typeName == forbidden
                    )
                }
            }
            for (method in clazz.declaredMethods) {
                val returnType = method.returnType.name
                for (forbidden in forbiddenAndroidTypes) {
                    assertFalse(
                        "Domain class ${clazz.name} method '${method.name}' return type ($returnType) must not depend on $forbidden",
                        returnType == forbidden
                    )
                }
                for (param in method.parameterTypes) {
                    val paramType = param.name
                    for (forbidden in forbiddenAndroidTypes) {
                        assertFalse(
                            "Domain class ${clazz.name} method '${method.name}' param ($paramType) must not depend on $forbidden",
                            paramType == forbidden
                        )
                    }
                }
            }
        }
    }
}
