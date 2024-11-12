package no.bankaxept.home.assignment.web;

import no.bankaxept.home.assignment.service.exception.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseBody
    public ResponseEntity<String> handleInsufficientFundsException(InsufficientFundsException exception) {
        exception.printStackTrace();
        String response = "{" +
                "\"error\":\"Insufficient funds.\"," +
                "\"bank\":\"" + exception.bankName + "\"," +
                "\"reason\":\" You tried to pay '" + exception.amount + "\"', but have only '" + exception.currentBalance + "' available.\"" +
                "}";

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
