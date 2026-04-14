package com.wex.purchasetransactions.repository;

import com.wex.purchasetransactions.model.PurchaseTransaction;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<PurchaseTransaction> hasId(Long id) {
        if (id == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<PurchaseTransaction> descriptionContains(String description) {
        if (description == null || description.isBlank()) {
            return Specification.unrestricted();
        }
        String escaped = description.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + escaped + "%", '\\');
    }

    public static Specification<PurchaseTransaction> transactionDateFrom(LocalDate startDate) {
        if (startDate == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate);
    }

    public static Specification<PurchaseTransaction> transactionDateTo(LocalDate endDate) {
        if (endDate == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), endDate);
    }
}
