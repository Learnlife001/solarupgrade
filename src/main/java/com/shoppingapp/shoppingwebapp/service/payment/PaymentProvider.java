package com.shoppingapp.shoppingwebapp.service.payment;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * One way of taking money.
 *
 * <p>This exists because the shop is worth more able to change payment
 * provider than tied to the one it started with. PayPal cannot charge naira,
 * which is the currency this catalogue is priced in, so the shop it is
 * deployed for will need a Nigerian provider — and anybody who takes this
 * codebase on will be in a different country with a different obvious choice
 * again. Adding one should be writing a class, not editing the service that
 * every payment flows through.
 *
 * <p><b>The rule the interface is shaped around:</b> an order becomes PAID only
 * because a provider told us, in an exchange <em>we</em> initiated, that money
 * moved. A buyer's browser arriving at a return URL is not that — a URL can be
 * typed, bookmarked, shared or replayed. So {@link #begin} hands the buyer off,
 * and the evidence is the response to {@link #capture} or a webhook proved
 * genuine by {@link #verifyWebhook}. An implementation that returns
 * {@code completed} from anything a browser said would defeat every other
 * safeguard in this application.
 *
 * <h2>Adding a provider</h2>
 * <ol>
 *   <li>Add a constant to {@link PaymentMethod} if the shop does not have one.</li>
 *   <li>Implement this interface, annotated {@code @Component} and
 *       {@code @ConditionalOnProperty} on its own credentials, so a deployment
 *       without them simply does not offer that method.</li>
 *   <li>Nothing else. {@code PaymentProviders} finds it, the checkout offers it
 *       when {@link #isConfigured()}, and the existing
 *       {@code /payments/{provider}/…} routes serve it.</li>
 * </ol>
 */
public interface PaymentProvider {

    /**
     * Short, stable, URL-safe: it is the {@code {provider}} segment of the
     * return, cancel and webhook URLs. Changing it breaks the webhook already
     * registered with the provider, which is how payments start silently not
     * settling.
     */
    String id();

    /** Which method on the checkout form this provider serves. */
    PaymentMethod method();

    /**
     * Whether this deployment actually has credentials for it.
     *
     * <p>False keeps the method off the checkout. A payment button that cannot
     * take money is worse than an absent one: the customer has already decided
     * to buy by the time it fails.
     */
    boolean isConfigured();

    /**
     * Creates the payment and says where to send the buyer.
     *
     * <p>The amount comes from the order's own snapshot rather than a fresh
     * conversion, so the buyer pays the figure they were quoted.
     */
    Checkout begin(Order order, String returnUrl, String cancelUrl);

    /**
     * Asks the provider to take the money it is holding.
     *
     * <p>Called when the buyer returns. Its answer is the evidence the order
     * was paid, which is why the return URL alone settles nothing.
     */
    CaptureResult capture(Order order);

    /**
     * Whether this provider can send money back from here.
     *
     * <p>Default false, so a provider is never assumed capable of something it
     * has not implemented. A shop that thinks it can refund and cannot is worse
     * than one that knows it cannot: the refund button would fail in front of a
     * customer already owed their money.
     */
    default boolean canRefund() {
        return false;
    }

    /**
     * Sends the whole charge back.
     *
     * <p>Full refunds only. Part-refunds mean deciding which line was returned,
     * what happens to delivery, and how the remainder is represented on an
     * order -- none of which this application models, and all of which would be
     * guessed at by an implementation that pretended otherwise.
     */
    default RefundResult refund(Order order) {
        throw new PaymentException(id() + " cannot make refunds from here");
    }

    /**
     * Whether webhooks can be proved genuine on this deployment.
     *
     * <p>Usually a signing secret. False means every webhook is ignored: a
     * notification that cannot be verified is indistinguishable from one an
     * attacker sent, and acting on it would mean dispatching goods on request.
     */
    boolean canVerifyWebhooks();

    boolean verifyWebhook(Map<String, String> headers, String rawBody);

    /**
     * The headers this provider signs with, so the controller can collect them
     * without knowing whose they are.
     */
    String[] signatureHeaders();

    /**
     * Reads a verified webhook body into the few facts settlement needs.
     *
     * <p>Empty for anything not worth acting on — the wrong event type, or a
     * body that does not carry our order id. Providers send many events; this
     * is where a provider's vocabulary stops and the shop's begins.
     */
    Optional<PaymentEvent> readWebhook(String rawBody);

    /** Where to send the buyer, and what to remember the payment by. */
    record Checkout(String reference, String redirectUrl) {
    }

    /**
     * @param completed true only when the provider confirms the money moved
     * @param status    the provider's own word for it, for the log line
     * @param reference the provider's id for this movement of money, which is
     *                  what a refund is later made against. Distinct from the
     *                  id of the order created at the provider, and null on a
     *                  provider that does not tell the two apart.
     */
    record CaptureResult(boolean completed, String status, String reference,
                         BigDecimal amount, String currency) {
    }

    /**
     * @param completed true only when the provider confirms the money went back
     * @param reference the provider's id for the refund, kept so the order can
     *                  be reconciled against the provider's own records
     */
    record RefundResult(boolean completed, String status, String reference,
                        BigDecimal amount, String currency) {
    }

    /**
     * A payment the provider says has completed.
     *
     * @param orderId   our order id, which the provider carries for us
     * @param reference the provider's id for the payment, checked against the
     *                  one we started so a genuine notification for somebody
     *                  else's payment cannot settle this order
     * @param captureReference the id a refund would later be made against.
     *                  Carried here because an order settled by webhook -- the
     *                  buyer closed the tab -- has to be as refundable as one
     *                  settled on the return journey.
     */
    record PaymentEvent(Long orderId, String reference, String captureReference,
                        BigDecimal amount, String currency) {
    }
}
