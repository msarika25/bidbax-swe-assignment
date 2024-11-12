package no.bankaxept.home.assignment.web;

import no.bankaxept.home.assignment.model.Transaction;
import no.bankaxept.home.assignment.service.PaymentService;
import no.bankaxept.home.assignment.service.exception.BankValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private Transaction transaction;

    @BeforeEach
    public void setUp() {
        transaction = new Transaction();
        transaction.setCardNumber("1234567812345678");
        transaction.setBank("TestBank");
        transaction.setAmount(100);
    }

    @Test
    public void testPay_Success() throws Exception {
        // Mock the service response

        Map<String, Object> mockedPaymentResponse = new HashMap<>();
        mockedPaymentResponse.put("balance", 900);
        mockedPaymentResponse.put("duration", "3ms");

        when(paymentService.pay(Mockito.any(), Mockito.anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mockedPaymentResponse));

        // Perform the POST request and verify the response
        mockMvc.perform(post("/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":\"100\",\"transactionTimestamp\":\"2024-11-10T12:00:00.000+00:00\",\"currentAccountBalance\":\"900.0\",\"duration\":\"3ms\"}"));
    }

    @Test
    public void testPay_DatabaseError() throws Exception {
        // Simulate a DataAccessException
        when(paymentService.pay(Mockito.any(), Mockito.anyString()))
                .thenThrow(new RuntimeException("Database error"));

        // Perform the POST request and verify the response
        mockMvc.perform(post("/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"bank\":\"TestBank\",\"error\":\"Database error\",\"reason\":\"Database error\"}"));
    }

    @Test
    public void testPay_BankValidationException() throws Exception {
        // Simulate a BankValidationException
        when(paymentService.pay(Mockito.any(), Mockito.anyString()))
                .thenThrow(new BankValidationException("TestBank", "Bank validation failed"));

        // Perform the POST request and verify the response
        mockMvc.perform(post("/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"bank\":\"TestBank\",\"error\":\"Bank validation failed\",\"reason\":\"Bank validation failed\"}"));
    }

    @Test
    public void testPay_IOError() throws Exception {
        // Simulate an IOException
        when(paymentService.pay(Mockito.any(), Mockito.anyString()))
                .thenThrow(new IOException("IO error"));

        // Perform the POST request and verify the response
        mockMvc.perform(post("/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"bank\":\"TestBank\",\"error\":\"IO error\",\"reason\":\"IO error\"}"));
    }

    @Test
    public void testPay_URISyntaxException() throws Exception {
        // Simulate a URISyntaxException
        when(paymentService.pay(Mockito.any(), Mockito.anyString()))
                .thenThrow(new URISyntaxException("uri", "URI error"));

        // Perform the POST request and verify the response
        mockMvc.perform(post("/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"1234567812345678\",\"bank\":\"TestBank\",\"amount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"bank\":\"TestBank\",\"error\":\"URI error\",\"reason\":\"URI error\"}"));
    }
}