package no.bankaxept.home.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.bankaxept.home.assignment.model.Transaction;
import no.bankaxept.home.assignment.service.exception.BankValidationException;
import no.bankaxept.home.assignment.service.exception.InsufficientFundsException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PaymentService {

    public JdbcTemplate jdbcTemplate;
    public RestTemplate restTemplate;
    public ObjectMapper objectMapper;

    public PaymentService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> pay(Transaction transaction, String transactionUuid) throws IOException, URISyntaxException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Integer currentBalance = jdbcTemplate.query("SELECT * FROM balance WHERE cardNumber = '" + transaction.cardNumber + "'",
                new ResultSetExtractor<Integer>() {

                    @Override
                    public Integer extractData(ResultSet rs) throws SQLException, DataAccessException {
                        rs.next();
                        return rs.getInt("amount");
                    }
                });

        if (currentBalance < transaction.amount) {
            throw new InsufficientFundsException(currentBalance, transaction.amount, transaction.bank);
        }

        if ("The Big Bank".equals(transaction.bank)) {
            if (transaction.amount > 200) {
                jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                        transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                        + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'small')");
            } else if (transaction.amount < 200) {
                ResponseEntity<String> bigBankResponse = restTemplate.getForEntity("http://fake.bigbank.no/check/" + transaction.amount, String.class);
                JsonNode jsonNode = objectMapper.readTree(bigBankResponse.getBody());
                boolean successful = jsonNode.get("successful").asBoolean();
                if (successful) {
                    jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                            transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                            + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'big')");
                } else {
                    throw new BankValidationException(transaction.bank, "Not successful");
                }
            }
        } else if (transaction.amount < 100) {
            if ("The Cashiers".equals(transaction.bank)) {
                jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                        transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                        + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'small')");
            } else if ("Loaners".equals(transaction.bank)) {
                jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                        transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                        + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'small')");
            }
        } else if (transaction.amount > 100) {
            if ("The Cashiers".equals(transaction.bank)) {
                ResponseEntity<Object> bigBankResponse = restTemplate.getForEntity(new URI("http://fake.cashiers.no/payment/"
                        + transaction.cardNumber + "?amount=" + transaction.amount), Object.class);
                if (bigBankResponse.getStatusCodeValue() == 200) {
                    jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                            transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                            + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'big')");
                } else {
                    throw new BankValidationException(transaction.bank, "Error Status Code: " + bigBankResponse.getStatusCodeValue());
                }
            } else if ("Loaners".equals(transaction.bank)) {
                LoanersRequest request = new LoanersRequest(transaction.cardNumber, transaction.amount);
                ResponseEntity<LoanersResponse> responseEntity = restTemplate.postForEntity(new URI("http://fake.loaners.no/payment/check"),
                        request, LoanersResponse.class);
                LoanersResponse loanersResponse = responseEntity.getBody();
                if (loanersResponse.responseCode == 1) {
                    if (loanersResponse.status == "Approved") {
                        jdbcTemplate.update("INSERT INTO transaction (bank, uuid, card, amount, date, type) VALUES ('" +
                                transaction.bank + "', '" + transaction.cardNumber + "', '" + transactionUuid + "', "
                                + transaction.amount + ", '" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', 'big')");
                    } else {
                        throw new BankValidationException(transaction.bank, "Transaction not approved. Status: " + loanersResponse.status);
                    }
                } else {
                    throw new BankValidationException(transaction.bank, "Error status code: " + loanersResponse.responseCode + ", with reason: " + loanersResponse.errorReason);
                }
            }
        }

        long newBalance = currentBalance - transaction.amount;
        jdbcTemplate.update("UPDATE balance SET amount = " + newBalance + " WHERE cardNumber = '" + transaction.cardNumber + "'");

        stopWatch.stop();

        Map<String, Object> result = new HashMap<>();
        result.put("balance", newBalance);
        result.put("duration", stopWatch.getTotalTimeMillis());
        return result;
    }

    private static class LoanersResponse {
        public int responseCode;
        public String status;
        public String errorReason;

        public LoanersResponse(int responseCode, String status, String errorReason) {
            this.responseCode = responseCode;
            this.status = status;
            this.errorReason = errorReason;
        }
    }

    private static class LoanersRequest {
        public String cardNumber;
        public int amount;

        public LoanersRequest(String cardNumber, int amount) {
            this.cardNumber = cardNumber;
            this.amount = amount;
        }
    }

}