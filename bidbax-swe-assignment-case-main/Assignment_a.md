### User story
A simplified banking transaction system is required, which accepts user request for transaction, approves (or rejects) it and stores it in a local database. 
Criteria for transaction accepting/rejecting:
- if requested amount is bigger than current balance, the transaction should be rejected
- for `small` transaction, where amount is below threshold (defined for each bank individually), no additional check is necessary
- for `big` transaction, where amount is above threshold (defined for each bank individually), additional REST call towards bank system is necessary

### Technical design
System consists of two database tables:
- `balance` which keeps track of current balance for every card
- `transaction` which keeps track of all successful transactions in the system 

Each of three banks defined in this project have their own REST API with following specifications:
- **The Big Bank**
  - HTTP GET http://fake.bigbank.no/check/${amount}
  - response ```{"successful": true|false}```
- **Loaners**
  - HTTP POST http://fake.loaners.no/payment/check
  - request body ```{"cardNumber":${cardNumber}, "amount":${amount}}```
  - response ```{"responseCode":1|2, "status":"Approved|Rejected", "errorReason":"Free text, present if the value of responseCode is 2"}```
- **The Cashiers**
  - HTTP GET http://fake.cashiers.no/payment/${cardNumber}?amount=${amount}
  - response ```<Response><card>${cardNumber}</card><amount>${amount}</amount></Response>``` 
 
Due to the nature of this project, following concepts should be kept in mind:
- in-memory H2 database should be used
- we assume that the requested `cardNumber` always corresponds to the requested `bank` (there is no need to check if the card is issued by the bank)
- we assume that the bank URLs are fully up and running and return response appropriate for our needs
- If you are familiar with the payment domain, you may notice that this simplified banking transaction system is not 
  alike real system, because the purpose of the assignment is to asses your development skills

 
### Prerequisites
- Java 8
- Maven 
- IDE (IntelliJ, Eclipse,...) 
 
### Assignment 
Currently, the project is in the POC phase. The product was presented to the stakeholders, which approved it.


**Your task is to refactor the existing code, to address issues, bugs or poor quality in the solution, so we can have production ready artifact. 
The only limitation is that REST API structure shouldn't be changed**

After finishing the task, you should pack the entire project and send it back to us, and we will discuss it on the technical interview.
*If you find some information missing or not clearly specified, you can take your own assumption(s) (which can be the simplest one) and note it down. 
Also, if you find that some crucial concepts are missing, and you think that it would take a lot of time to implement, 
feel free to just comment it, and we can discuss them on the technical interview.*

Our goal is to assess your analytical and development skills on the **existing** code (concepts like authorization, authentication and encryption is not in the scope of this assignment). Keep in mind that there is no wrong solution (we will discuss provided solutions on the technical interview).

You shouldn't take more than 3 hours for solving this assignment. If you need more time for solving issues, you can solve the most important ones by your opinion and comment out the proposed solutions for the rest.