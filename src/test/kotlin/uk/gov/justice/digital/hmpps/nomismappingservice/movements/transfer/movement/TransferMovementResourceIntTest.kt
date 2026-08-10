package uk.gov.justice.digital.hmpps.nomismappingservice.movements.transfer.movement

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.nomismappingservice.helper.TestDuplicateErrorResponse
import uk.gov.justice.digital.hmpps.nomismappingservice.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.nomismappingservice.integration.isDuplicateMapping
import uk.gov.justice.digital.hmpps.nomismappingservice.movements.transfer.schedule.TransferMappingType
import java.util.UUID

class TransferMovementResourceIntTest(
  @Autowired private val movementRepository: TransferMovementRepository,
) : IntegrationTestBase() {

  @Nested
  @DisplayName("POST /mapping/transfer-scheduler/movement")
  inner class CreateTransferMovementMapping {

    @AfterEach
    fun tearDown() = runTest {
      movementRepository.deleteAll()
    }

    @Nested
    inner class HappyPath {
      val mapping = TransferMovementMappingDto(
        "A1234BC",
        12345L,
        3,
        UUID.randomUUID(),
        TransferMappingType.NOMIS_CREATED,
      )

      @Test
      fun `should create mapping`() = runTest {
        webTestClient.createTransferMovementMapping(mapping)
          .expectStatus().isCreated

        with(movementRepository.findByNomisBookingIdAndNomisMovementSeq(mapping.nomisBookingId, mapping.nomisMovementSeq)!!) {
          assertThat(offenderNo).isEqualTo("A1234BC")
          assertThat(dpsTransferMovementId).isEqualTo(mapping.dpsTransferMovementId)
          assertThat(mappingType).isEqualTo(TransferMappingType.NOMIS_CREATED)
        }
      }
    }

    @Nested
    inner class Validation {
      val mapping = TransferMovementMappingDto(
        "A1234BC",
        12345L,
        3,
        UUID.randomUUID(),
        TransferMappingType.NOMIS_CREATED,
      )
      val duplicateMappingDps = TransferMovementMappingDto(
        "B2345CD",
        56789L,
        4,
        mapping.dpsTransferMovementId,
        TransferMappingType.MIGRATED,
      )
      val duplicateMappingNomis = TransferMovementMappingDto(
        "C3456DE",
        12345L,
        3,
        UUID.randomUUID(),
        TransferMappingType.MIGRATED,
      )

      @Test
      fun `should reject duplicate DPS ID mapping`() = runTest {
        webTestClient.createTransferMovementMapping(mapping)
          .expectStatus().isCreated

        webTestClient.createTransferMovementMapping(duplicateMappingDps)
          .expectStatus().isDuplicateMapping
          .expectBody(object : ParameterizedTypeReference<TestDuplicateErrorResponse>() {})
          .returnResult().responseBody!!
          .apply {
            assertThat(moreInfo.existing)
              .containsEntry("prisonerNumber", mapping.prisonerNumber)
              .containsEntry("nomisBookingId", mapping.nomisBookingId.toInt())
              .containsEntry("nomisMovementSeq", mapping.nomisMovementSeq)
              .containsEntry("dpsTransferMovementId", mapping.dpsTransferMovementId.toString())
              .containsEntry("mappingType", mapping.mappingType.toString())
            assertThat(moreInfo.duplicate)
              .containsEntry("prisonerNumber", duplicateMappingDps.prisonerNumber)
              .containsEntry("nomisBookingId", duplicateMappingDps.nomisBookingId.toInt())
              .containsEntry("nomisMovementSeq", duplicateMappingDps.nomisMovementSeq)
              .containsEntry("dpsTransferMovementId", duplicateMappingDps.dpsTransferMovementId.toString())
              .containsEntry("mappingType", duplicateMappingDps.mappingType.toString())
          }
      }

      @Test
      fun `should reject duplicate NOMIS ID mapping`() = runTest {
        webTestClient.createTransferMovementMapping(mapping)
          .expectStatus().isCreated

        webTestClient.createTransferMovementMapping(duplicateMappingNomis)
          .expectStatus().isDuplicateMapping
          .expectBody(object : ParameterizedTypeReference<TestDuplicateErrorResponse>() {})
          .returnResult().responseBody!!
          .apply {
            assertThat(moreInfo.existing)
              .containsEntry("prisonerNumber", mapping.prisonerNumber)
              .containsEntry("nomisBookingId", mapping.nomisBookingId.toInt())
              .containsEntry("nomisMovementSeq", mapping.nomisMovementSeq)
              .containsEntry("dpsTransferMovementId", mapping.dpsTransferMovementId.toString())
              .containsEntry("mappingType", mapping.mappingType.toString())
            assertThat(moreInfo.duplicate)
              .containsEntry("prisonerNumber", duplicateMappingNomis.prisonerNumber)
              .containsEntry("nomisBookingId", duplicateMappingNomis.nomisBookingId.toInt())
              .containsEntry("nomisMovementSeq", duplicateMappingNomis.nomisMovementSeq)
              .containsEntry("dpsTransferMovementId", duplicateMappingNomis.dpsTransferMovementId.toString())
              .containsEntry("mappingType", duplicateMappingNomis.mappingType.toString())
          }
      }
    }

    @Nested
    inner class Security {
      val mapping = TransferMovementMappingDto(
        "A1234BC",
        12345L,
        3,
        UUID.randomUUID(),
        mappingType = TransferMappingType.NOMIS_CREATED,
      )

      @Test
      fun `access not authorised when no authority`() {
        webTestClient.post()
          .uri("/mapping/transfer-scheduler/movement")
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(mapping))
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post()
          .uri("/mapping/transfer-scheduler/movement")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(mapping))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post()
          .uri("/mapping/transfer-scheduler/movement")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(mapping))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    private fun WebTestClient.createTransferMovementMapping(mapping: TransferMovementMappingDto) = post()
      .uri("/mapping/transfer-scheduler/movement")
      .headers(setAuthorisation(roles = listOf("NOMIS_MAPPING_API__SYNCHRONISATION__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .body(BodyInserters.fromValue(mapping))
      .exchange()
  }

  @Nested
  @DisplayName("GET /mapping/transfer-scheduler/movement/nomis-id/{nomisBookingId}/{nomisMovementSeq}")
  inner class GetTransferMovementMappingByNomisId {

    @AfterEach
    fun tearDown() = runTest {
      movementRepository.deleteAll()
    }

    @Nested
    inner class HappyPath {
      val mapping = TransferMovementMapping(
        UUID.randomUUID(),
        23456L,
        3,
        "A1234BC",
        mappingType = TransferMappingType.NOMIS_CREATED,
      )

      @Test
      fun `should get transfer movement mapping by NOMIS ID`() = runTest {
        movementRepository.save(mapping)

        webTestClient.getTransferMovementMapping(mapping.nomisBookingId, mapping.nomisMovementSeq)
          .expectStatus().isOk
          .expectBody(object : ParameterizedTypeReference<TransferMovementMappingDto>() {})
          .returnResult().responseBody!!
          .apply {
            assertThat(nomisBookingId).isEqualTo(mapping.nomisBookingId)
            assertThat(nomisMovementSeq).isEqualTo(mapping.nomisMovementSeq)
            assertThat(dpsTransferMovementId).isEqualTo(mapping.dpsTransferMovementId)
            assertThat(prisonerNumber).isEqualTo(mapping.offenderNo)
            assertThat(mappingType).isEqualTo(mapping.mappingType)
          }
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return not found when mapping does not exist`() = runTest {
        webTestClient.getTransferMovementMapping(12345L, 3)
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access not authorised when no authority`() {
        webTestClient.get()
          .uri("/mapping/transfer-scheduler/movement/nomis-id/12345/3")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get()
          .uri("/mapping/transfer-scheduler/movement/nomis-id/12345/3")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get()
          .uri("/mapping/transfer-scheduler/movement/nomis-id/12345/3")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    private fun WebTestClient.getTransferMovementMapping(nomisBookingId: Long, nomisMovementSeq: Int) = get()
      .uri("/mapping/transfer-scheduler/movement/nomis-id/$nomisBookingId/$nomisMovementSeq")
      .headers(setAuthorisation(roles = listOf("NOMIS_MAPPING_API__SYNCHRONISATION__RW")))
      .exchange()
  }
}
