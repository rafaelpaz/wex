package com.wex.purchasetransactions.dto;

import java.util.List;

public record PaginatedTransactionResponse(
        List<PurchaseTransactionResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize) {}
