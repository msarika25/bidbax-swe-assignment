package no.bankaxept.home.assignment.model;

public class Transaction {

    public String cardNumber;
    public String bank;
    public int amount;

    public Transaction(String cardNumber, int amount, String the_big_bank) {
        this.cardNumber= cardNumber;
        this.amount=amount;
        this.bank=the_big_bank;
    }

    public Transaction() {

    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
