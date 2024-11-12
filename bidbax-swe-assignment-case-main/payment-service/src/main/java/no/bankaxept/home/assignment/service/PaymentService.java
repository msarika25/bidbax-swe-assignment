package no.bankaxept.home.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.bankaxept.home.assignment.model.Transaction;
import no.bankaxept.home.assignment.service.exception.BankValidationException;
import no.bankaxept.home.assignment.service.exception.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private static final int BIG_BANK_THRESHOLD = 200;
    private static final int LOANERS_THRESHOLD = 100;
    private static final long MAX_PROCESSING_TIME_MS = 30000;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }


    @Async
    @Transactional
    public CompletableFuture<Map<String, Object>> pay(Transaction transaction, String transactionUuid) throws IOException, URISyntaxException {
        logger.info("Starting payment process for transaction ID: {}", transactionUuid);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        // Check if transaction with this UUID has already been processed
        if (isTransactionProcessed(transactionUuid)) {
            throw new IOException("Duplicate transaction detected.");
        }

        int currentBalance = getCurrentBalance(transaction.cardNumber)
                .orElseThrow(() -> {
                    logger.error("No balance found for card: {}", transaction.cardNumber);
                    return new DataAccessException("No balance found for card: " + transaction.cardNumber) {};
                });

        validateSufficientFunds(currentBalance, transaction);

        boolean isBigTransaction = isBigTransaction(transaction);
        logger.info("Transaction {} is classified as {}", transactionUuid, isBigTransaction ? "big" : "small");

        if (isBigTransaction) {
            validateBigTransactionWithBank(transaction, transactionUuid);
        }

        recordTransaction(transaction, transactionUuid, isBigTransaction ? "big" : "small");

        int newBalance = updateBalance(transaction, currentBalance);

        stopWatch.stop();

        if (stopWatch.getTotalTimeMillis() > MAX_PROCESSING_TIME_MS) { // If processing takes longer than 30 seconds
            logger.warn("Payment processing for transaction ID: {} took too long: {} ms", transactionUuid, stopWatch.getTotalTimeMillis());
            throw new IOException("Payment processing took too long.");
        }

        logger.info("Payment processed successfully for transaction ID: {}. New balance: {}", transactionUuid, newBalance);
        return CompletableFuture.completedFuture(createResponse(newBalance, stopWatch.getTotalTimeMillis()));
    }

    boolean isTransactionProcessed(String transactionUuid) {
        logger.info("Checking if transaction with UUID: {} has already been processed", transactionUuid);

        String sql = "SELECT COUNT(*) FROM transaction WHERE uuid = ?";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{transactionUuid}, Integer.class);

        boolean isProcessed = count != null && count > 0;
        logger.info("Transaction with UUID: {} is already processed: {}", transactionUuid, isProcessed);

        return isProcessed;
    }

    private Optional<Integer> getCurrentBalance(String cardNumber) {
        String sql = "SELECT amount FROM balance WHERE cardNumber = ?";
        try {
            return jdbcTemplate.query(sql, new Object[]{cardNumber}, (ResultSet rs) -> rs.next() ? Optional.of(rs.getInt("amount")) : Optional.empty());
        } catch (DataAccessException e) {
            logger.error("Error retrieving balance for card: {}", cardNumber, e);
            return Optional.empty();
        }
    }

    void validateSufficientFunds(int currentBalance, Transaction transaction) {
        if (currentBalance < transaction.amount) {
            logger.warn("Insufficient funds for transaction ID: {}. Available: {}, Required: {}", transaction.cardNumber, currentBalance, transaction.amount);
            throw new InsufficientFundsException(currentBalance, transaction.amount, transaction.bank);
        }
    }

    private boolean isBigTransaction(Transaction transaction) {
        return "The Big Bank".equals(transaction.bank) ? transaction.amount > BIG_BANK_THRESHOLD : transaction.amount > LOANERS_THRESHOLD;
    }

    @Retryable(value = { ResourceAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    void validateBigTransactionWithBank(Transaction transaction, String transactionUuid) throws IOException, URISyntaxException {
        switch (transaction.bank) {
            case "The Big Bank":
                validateBigBank(transaction, transactionUuid);
                break;
            case "The Cashiers":
                validateCashiers(transaction, transactionUuid);
                break;
            case "Loaners":
                validateLoaners(transaction, transactionUuid);
                break;
            default:
                logger.error("Unsupported bank: {}", transaction.bank);
                throw new BankValidationException(transaction.bank, "Unsupported bank.");
        }
    }

    private void validateBigBank(Transaction transaction, String transactionUuid) throws IOException {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity("http://fake.bigbank.no/check/" + transaction.amount, String.class, transactionUuid);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            if (!jsonNode.get("successful").asBoolean()) {
                throw new BankValidationException(transaction.bank, "Transaction not approved by Big Bank.");
            }
        } catch (ResourceAccessException e) {
            logger.error("Payment API timeout for Big Bank on transaction: {}", transactionUuid);
            throw new IOException("Big Bank Service is currently unavailable. Please try again later.", e);
        }
    }

    private void validateCashiers(Transaction transaction, String transactionUuid) throws IOException {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity("http://fake.cashiers.no/payment/" + transaction.cardNumber + "?amount=" + transaction.amount,
                    String.class,transactionUuid);
            if (response.getStatusCode().isError()) {
                throw new BankValidationException(transaction.bank, "Transaction failed with Cashiers.");
            }
        } catch (ResourceAccessException e) {
            logger.error("Payment API timeout for Cashier on transaction: {}", transactionUuid);
            throw new IOException("Payment service is currently unavailable. Please try again later.", e);

        }
    }

    private void validateLoaners(Transaction transaction, String transactionUuid) throws IOException {
        try {
            LoanersRequest request = new LoanersRequest(transaction.cardNumber, transaction.amount);
            ResponseEntity<LoanersResponse> response = restTemplate.postForEntity("http://fake.loaners.no/payment/check", request, LoanersResponse.class, transactionUuid);
            Optional.ofNullable(response.getBody())
                    .filter(resp -> resp.responseCode == 1 && "Approved".equals(resp.status))
                    .orElseThrow(() -> new BankValidationException(transaction.bank, "Transaction not approved by Loaners."));
        }catch (ResourceAccessException e) {
            logger.error("Payment API timeout for Loaners on transaction: {}", transactionUuid);
            throw new IOException("Loaners service is currently unavailable. Please try again later.", e);
        }
    }

    private void recordTransaction(Transaction transaction, String transactionUuid, String type) {
        String sql = "INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, transaction.bank, transactionUuid, transaction.cardNumber, transaction.amount, getCurrentDate(), type);
        logger.info("Recorded transaction ID: {} of type: {}", transactionUuid, type);
    }

    int updateBalance(Transaction transaction, int currentBalance) {
        int newBalance = currentBalance - transaction.amount;
        jdbcTemplate.update("UPDATE balance SET amount = ? WHERE cardNumber = ?", newBalance, transaction.cardNumber);
        logger.info("Updated balance for card: {}. New balance: {}", transaction.cardNumber, newBalance);
        return newBalance;
    }

    private Map<String, Object> createResponse(int newBalance, long responseDuration) {
        Map<String, Object> response = new HashMap<>();
        response.put("balance", newBalance);
        response.put("duration", responseDuration);
        logger.info("Response created with balance: {} and duration: {} ms", newBalance, responseDuration);
        return response;
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    // Nested static classes for LoanersRequest and LoanersResponse

    private static class LoanersRequest {
        public String cardNumber;
        public int amount;

        public LoanersRequest(String cardNumber, int amount) {
            this.cardNumber = cardNumber;
            this.amount = amount;
        }
    }

    private static class LoanersResponse {
        public int responseCode;
        public String status;
        public String errorReason;
    }
}