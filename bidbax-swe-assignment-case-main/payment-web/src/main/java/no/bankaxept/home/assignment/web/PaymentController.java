package no.bankaxept.home.assignment.web;

import no.bankaxept.home.assignment.model.Transaction;
import no.bankaxept.home.assignment.service.PaymentService;
import no.bankaxept.home.assignment.service.exception.BankValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(path = "/pay")
    @ResponseBody
    public String pay(@RequestBody Transaction transaction) {
        Map<String, Object> payment = null;
        try {
            payment = paymentService.pay(transaction, UUID.randomUUID().toString()).get();
        } catch (DataAccessException e) {
            return createErrorResponse(transaction.bank, "Database error", e.toString());
        } catch (IOException e) {
            return createErrorResponse(transaction.bank, "IO error", e.toString());
        } catch (URISyntaxException e) {
            return createErrorResponse(transaction.bank, "URI error", e.toString());
        } catch (BankValidationException e) {
            e.printStackTrace();
            return createErrorResponse(e.getBankName(), "Bank validation failed", e.message);
        } catch (ExecutionException e) {
            return createErrorResponse(transaction.bank, "Execution error", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResponse(transaction.bank, "Payment interrupted", e.toString());
        }

        return "{" +
                "\"cardNumber\":\"" + transaction.cardNumber + "\"," +
                "\"bank\":\"" + transaction.bank + "\"," +
                "\"amount\":\"" + transaction.amount + "\"," +
                "\"transactionTimestamp\":\"" + new Date() + "\"," +
                "\"currentAccountBalance\":\"" + payment.get("balance") + "\"" +
                "\"duration\":\"" + payment.get("duration") + "\"" +
                "}";
    }
    // Helper method to create error response in JSON format
    private String createErrorResponse(String bank, String errorType, String reason) {
        return "{" +
                "\"bank\":\"" + bank + "\"," +
                "\"error\":\"" + errorType + "\"," +
                "\"reason\":\"" + reason + "\"" +
                "}";
    }
}
