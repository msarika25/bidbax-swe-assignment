# System Design: Planning for failure

It's fairly easy to write code for the happy path- code executes quickly, all external api calls respond quickly, no errors, etc. Handling problems gracefully can be a bit more challenging.

Let's say you've got an online store and you use some external api to process payments. If all goes well, when the user clicks "submit order" on the website, your code creates an order in the database, calls the payment api (which usually returns in about 1 second or so), and then the UI for the user updates to let them know their order will be shipped.

Happy Path:
User clicks submit -> waits 1 - 2 seconds -> UI updates to say order is shipped.

Today, however, the payment processor is having issues and the api is very slow. Shortly after that starts, your customer service department is getting complaints from people saying that nothing happens when they submit an order. Other customers are reporting that they've been charged multiple times for one order.

For each scenario, describe what might have gone wrong, and then propose a solution.
## Unhappy Customer 1.
    User clicks submit -> waits 30 seconds -> nothing happens -> user is charged and order is shipped.
###Issue:
The delay in the payment API response might have caused the system to time out, leaving the UI without feedback for the user. However, the backend still processed the payment and shipped the order, leading to confusion for the customer.

###Solution:
1. Implement client-side and server-side timeout controls (e.g., 5-10 seconds) on the payment API call. If the payment doesn’t respond within this period, cancel the order process and inform the user that there’s a delay, allowing them to retry later.
2. Shift order processing to a queue where the payment and order status are processed asynchronously. Display an immediate response to the user that their order is “being processed” with an estimated time.
3. Use an unique key for each payment API request to ensure each payment attempt can be identified and managed uniquely, preventing accidental double charges in case of retries.
4. Set up an email or SMS notification system to alert customers of the successful order and payment status once the issue is resolved.

## Unhappy Customer 2.
    User clicks submit -> waits 45 seconds -> user is charged twice -> UI updates to say order is shipped.
###Issue:
This could happen due to network or system retries not managed with idempotency, leading to duplicate charges without confirmation from the original request.

###Solution:
1. When the order is created, generate a unique transaction identifier and pass this to the payment processor. If a retry happens, the system checks if this identifier has already been processed and cancels any duplicate attempts.
2. Ensure that if payment processing fails or stalls, the order is rolled back to prevent any incomplete or duplicate transactions.
3. Inform the user if the transaction is taking longer than expected (e.g., “Your order is being processed. Please do not refresh or click again”). Include a progress bar or estimated wait time to reassure the user

## Unhappy Customer 3.
    User clicks submit -> nothing happens -> user clicks submit again -> UI updates to say order is shipped. User was charged twice, order shipped once.
###Issue:
The absence of response feedback and idempotency in the API call leads to double charges on the same order.

###Solution:
1. Apply an unique key or transaction lock for each order submission. When the user clicks “submit,” the system should mark the order request as “pending” and lock further payment attempts until it’s completed or canceled.
2. After the first submit, display immediate feedback to the user indicating the order is in process. A simple loading indicator or a “Please wait” message would prevent the user from clicking multiple times.
3. Implement a client-side debounce to disable the submit button temporarily after the user clicks it. This minimizes accidental multiple submissions.
4. If the initial payment request fails, queue it for automatic retry at intervals, ensuring only one payment is attempted at a time. Notify the user of any delays via email or text message if the issue persists.