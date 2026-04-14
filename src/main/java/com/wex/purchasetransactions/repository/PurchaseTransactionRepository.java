package com.wex.purchasetransactions.repository;

import com.wex.purchasetransactions.model.PurchaseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransaction, Long>,JpaSpecificationExecutor<PurchaseTransaction> {}
