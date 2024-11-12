package no.bankaxept.home.assignment.service.exception;

public class BankValidationException extends RuntimeException {
    public String message;
    private String bankName;

    public BankValidationException(String bankName, String message) {
        super(message);
        this.bankName = bankName;
    }

    public String getBankName() {
        return bankName;
    }
}
