package uk.gov.justice.digital.hmpps.nomismappingservice.movements.transfer.movement

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.nomismappingservice.config.DuplicateMappingException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@Validated
@PreAuthorize("hasRole('NOMIS_MAPPING_API__SYNCHRONISATION__RW')")
@RequestMapping("/mapping/transfer-scheduler/movement", produces = [MediaType.APPLICATION_JSON_VALUE])
class TransferMovementResource(
  private val service: TransferMovementService,
) {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a mapping for a single transfer movement",
    description = "Creates a mapping for a single transfer movement. Requires ROLE_NOMIS_MAPPING_API__SYNCHRONISATION__RW",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [Content(mediaType = "application/json", schema = Schema(implementation = TransferMovementMappingDto::class))],
    ),
    responses = [
      ApiResponse(responseCode = "201", description = "Transfer movement mapping created"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Access forbidden for this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "The mapping already exists.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun createTransferMovementMapping(
    @RequestBody mapping: TransferMovementMappingDto,
  ) = try {
    service.createMovementMapping(mapping)
  } catch (dke: DuplicateKeyException) {
    val existing = getExistingTransferMovementMappingSimilarTo(mapping)
    throw DuplicateMappingException(
      messageIn = "Transfer movement mapping already exists",
      duplicate = mapping,
      existing = existing,
      cause = dke,
    )
  }

  private suspend fun getExistingTransferMovementMappingSimilarTo(mapping: TransferMovementMappingDto) = runCatching {
    service.getMovementMappingByNomisId(mapping.nomisBookingId, mapping.nomisMovementSeq)
  }
    .getOrElse {
      service.getMovementMappingByDpsId(mapping.dpsTransferMovementId)
    }

  @GetMapping("/nomis-id/{nomisBookingId}/{nomisMovementSeq}")
  @Operation(
    summary = "Gets a mapping for a single transfer movement by NOMIS booking ID / movement seq.",
    description = "Gets a mapping for a single transfer movement by NOMIS booking ID / movement seq. Requires ROLE_NOMIS_MAPPING_API__SYNCHRONISATION__RW",
    responses = [
      ApiResponse(responseCode = "200", description = "Transfer movement mapping returned"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Access forbidden for this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "The mapping does not exist.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getTransferMovementMappingByNomisId(
    @PathVariable nomisBookingId: Long,
    @PathVariable nomisMovementSeq: Int,
  ) = service.getMovementMappingByNomisId(nomisBookingId, nomisMovementSeq)
}
