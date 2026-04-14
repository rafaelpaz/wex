package com.wex.purchasetransactions.service;

import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ConvertedTransactionResponse;
import com.wex.purchasetransactions.dto.ExchangeRateResult;
import com.wex.purchasetransactions.dto.PaginatedTransactionResponse;
import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.dto.PurchaseTransactionResponse;
import com.wex.purchasetransactions.exception.CurrencyConversionException;
import com.wex.purchasetransactions.exception.TransactionNotFoundException;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;
import com.wex.purchasetransactions.repository.TransactionSpecifications;
import com.wex.purchasetransactions.util.CurrencyConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseTransactionService.class);

    private final PurchaseTransactionRepository repository;
    private final TreasuryExchangeRateClient exchangeRateClient;
    private final CurrencyValidator currencyValidator;

    public PurchaseTransactionService(PurchaseTransactionRepository repository, TreasuryExchangeRateClient exchangeRateClient, CurrencyValidator currencyValidator) {
        this.repository = repository;
        this.exchangeRateClient = exchangeRateClient;
        this.currencyValidator = currencyValidator;
    }

    @Transactional
    public PurchaseTransaction store(PurchaseTransactionRequest request) {
        BigDecimal roundedAmount = request.purchaseAmount().setScale(2, RoundingMode.HALF_UP);
        PurchaseTransaction transaction = new PurchaseTransaction(request.description(), request.transactionDate(), roundedAmount);
        PurchaseTransaction saved = repository.save(transaction);
        log.info("Stored purchase transaction id={} amount={} date={}", saved.getId(), saved.getPurchaseAmount(), saved.getTransactionDate());
        return saved;
    }

    @Transactional(readOnly = true)
    public PaginatedTransactionResponse listTransactions(Long id, String description, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<PurchaseTransaction> spec = Specification
                .where(TransactionSpecifications.hasId(id))
                .and(TransactionSpecifications.descriptionContains(description))
                .and(TransactionSpecifications.transactionDateFrom(startDate))
                .and(TransactionSpecifications.transactionDateTo(endDate));

        Page<PurchaseTransaction> page = repository.findAll(spec, pageable);

        List<PurchaseTransactionResponse> content = page.getContent().stream()
                .map(PurchaseTransactionResponse::from)
                .toList();

        return new PaginatedTransactionResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    @Transactional(readOnly = true)
    public ConvertedTransactionResponse retrieveWithConversion(Long id, String currency) {
        log.info("Retrieving transaction id={} with conversion to currency={}", id, currency);

        currencyValidator.validate(currency);

        PurchaseTransaction transaction = repository.findById(id).orElseThrow(() -> {
            log.warn("Transaction not found id={}", id);
            return new TransactionNotFoundException("Transaction not found");
        });

        ExchangeRateResult rateResult = exchangeRateClient.getExchangeRate(currency, transaction.getTransactionDate()).orElseThrow(() -> {
            log.warn("No exchange rate available for currency={} transactionDate={}", currency, transaction.getTransactionDate());
            return new CurrencyConversionException("The purchase cannot be converted to the target currency");
        });

        BigDecimal convertedAmount = CurrencyConverter.convert(transaction.getPurchaseAmount(), rateResult.exchangeRate());
        log.info("Converted transaction id={} amount={} to {} at rate={} convertedAmount={}", id, transaction.getPurchaseAmount(), currency, rateResult.exchangeRate(), convertedAmount);

        return new ConvertedTransactionResponse(String.valueOf(transaction.getId()),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getPurchaseAmount(),
                rateResult.exchangeRate(),
                currency,
                convertedAmount);
    }
}
