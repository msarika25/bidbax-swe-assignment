package no.bankaxept.home.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.bankaxept.home.assignment.model.Transaction;
import no.bankaxept.home.assignment.service.exception.BankValidationException;
import no.bankaxept.home.assignment.service.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this); // Use this if you cannot upgrade Mockito
    }

    @Test
    void testPay_SuccessfulTransaction() throws Exception {
        Transaction transaction = new Transaction("1234", 150, "The Big Bank");
        String transactionUuid = "unique-uuid";

        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class))).thenReturn(0); // transaction not processed
        when(jdbcTemplate.query(anyString(), (Object[]) any(Object[].class), (ResultSetExtractor<Object>) any())).thenReturn(Optional.of(300)); // sufficient balance
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"successful\": true}", HttpStatus.OK)); // bank validation successful
        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));

        CompletableFuture<Map<String, Object>> future = paymentService.pay(transaction, transactionUuid);
        Map<String, Object> result = future.get();

        assertNotNull(result);
        assertTrue(result.containsKey("balance"));
        assertTrue(result.containsKey("duration"));
    }

    @Test
    void testPay_InsufficientFunds() {
        Transaction transaction = new Transaction("1234", 150, "The Big Bank");
        String transactionUuid = "unique-uuid";

        when(jdbcTemplate.query(anyString(), (Object[]) any(Object[].class), (ResultSetExtractor<Object>) any())).thenReturn(Optional.of(100)); // insufficient balance
        String expectedMessage = "Insufficient funds. Available: 100, Required: 150 for bank: The Big Bank";

        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> paymentService.pay(transaction, transactionUuid)
        );
        assertEquals("Insufficient funds", exception.getMessage());
    }

    @Test
    void testPay_DuplicateTransaction() {
        Transaction transaction = new Transaction("1234", 150, "The Big Bank");
        String transactionUuid = "unique-uuid";

        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class))).thenReturn(1); // transaction already processed

        IOException exception = assertThrows(
                IOException.class,
                () -> paymentService.pay(transaction, transactionUuid)
        );
        assertEquals("Duplicate transaction detected.", exception.getMessage());
    }

    @Test
    void testValidateBigTransactionWithUnsupportedBank() {
        Transaction unsupportedTransaction = new Transaction();
        unsupportedTransaction.setBank("Unsupported Bank");

        BankValidationException exception = assertThrows(BankValidationException.class, () -> {
            paymentService.validateBigTransactionWithBank(unsupportedTransaction, UUID.randomUUID().toString());
        });

        assertEquals("Unsupported bank.", exception.getMessage());
    }

    @Test
    void testIsTransactionProcessed_True() {
        String transactionUuid = "uuid-123";

        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class))).thenReturn(1);

        boolean result = paymentService.isTransactionProcessed(transactionUuid);
        assertTrue(result);
    }

    @Test
    void testIsTransactionProcessed_False() {
        String transactionUuid = "uuid-123";

        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class))).thenReturn(0);

        boolean result = paymentService.isTransactionProcessed(transactionUuid);
        assertFalse(result);
    }

    @Test
    void testUpdateBalance() {
        Transaction transaction = new Transaction("1234", 100, "The Big Bank");
        int currentBalance = 300;

        when(jdbcTemplate.update(anyString(), anyInt(), anyString())).thenReturn(1);

        int newBalance = paymentService.updateBalance(transaction, currentBalance);

        assertEquals(200, newBalance);
    }


}
