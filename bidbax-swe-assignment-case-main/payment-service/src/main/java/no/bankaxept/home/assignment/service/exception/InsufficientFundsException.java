package no.bankaxept.home.assignment.service.exception;

public class InsufficientFundsException extends RuntimeException {
    public int currentBalance;
    public int amount;
    public String bankName;

    // Constructor with message
    public InsufficientFundsException(int currentBalance, int amount, String bankName) {
        super("Insufficient funds");
        this.currentBalance = currentBalance;
        this.amount = amount;
        this.bankName = bankName;
    }
}
