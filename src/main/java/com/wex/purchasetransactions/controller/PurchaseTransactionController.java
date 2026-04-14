package com.wex.purchasetransactions.controller;

import com.wex.purchasetransactions.dto.ConvertedTransactionResponse;
import com.wex.purchasetransactions.dto.ErrorResponse;
import com.wex.purchasetransactions.dto.PaginatedTransactionResponse;
import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.dto.PurchaseTransactionResponse;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.service.PurchaseTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/purchase-transactions")
@Tag(
        name = "Purchase Transactions",
        description = "Store and retrieve purchase transactions with currency conversion")
public class PurchaseTransactionController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "description", "transactionDate", "purchaseAmount", "createdAt", "updatedAt");

    private final PurchaseTransactionService service;

    public PurchaseTransactionController(PurchaseTransactionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Store a purchase transaction",
            description = "Persists a new purchase transaction with a description, transaction date, and US dollar amount. "
                    + "The amount is rounded to the nearest cent before storing. A unique identifier is assigned automatically.")
    @ApiResponse(
            responseCode = "201",
            description = "Transaction created successfully",
            content = @Content(schema = @Schema(implementation = PurchaseTransactionResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. description too long, non-positive amount, invalid date)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PurchaseTransactionResponse> createTransaction(@Valid @RequestBody PurchaseTransactionRequest request) {
        PurchaseTransaction entity = service.store(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PurchaseTransactionResponse.from(entity));
    }

    @GetMapping
    @Operation(
            summary = "List purchase transactions",
            description = "Returns a paginated list of purchase transactions with optional filters for description and date range.")
    @ApiResponse(
            responseCode = "200",
            description = "Page of transactions returned",
            content = @Content(schema = @Schema(implementation = PaginatedTransactionResponse.class)))
    public ResponseEntity<PaginatedTransactionResponse> listTransactions(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        Pageable pageable = buildPageable(page, size, sort);
        PaginatedTransactionResponse response = service.listTransactions(id, description, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(property)) {
            throw new IllegalArgumentException("Invalid sort field: '" + property + "'. Allowed fields: " + SORTABLE_FIELDS);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieve a purchase transaction with currency conversion",
            description = "Retrieves a stored purchase transaction and converts the US dollar amount to the specified "
                    + "target currency using the Treasury Reporting Rates of Exchange API. The most recent exchange rate "
                    + "within 6 months of the transaction date is used.")
    @ApiResponse(
            responseCode = "200",
            description = "Transaction retrieved with currency conversion",
            content = @Content(schema = @Schema(implementation = ConvertedTransactionResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Conversion not available or invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Transaction not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Exchange rate service temporarily unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ConvertedTransactionResponse> getTransactionWithConversion(
            @Parameter(description = "Unique identifier of the purchase transaction", required = true, schema = @Schema(type = "integer", format = "int64")) @PathVariable Long id,
            @Parameter(description = "Target currency for conversion (e.g. 'Canada-Dollar', 'Brazil-Real')", required = true) @RequestParam @NotBlank(message = "Currency must not be blank") String currency) {
        ConvertedTransactionResponse response = service.retrieveWithConversion(id, currency);
        return ResponseEntity.ok(response);
    }
}
