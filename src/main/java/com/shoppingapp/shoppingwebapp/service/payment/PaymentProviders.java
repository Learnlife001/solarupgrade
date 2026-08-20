package com.shoppingapp.shoppingwebapp.service.payment;

import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every payment provider this deployment has, indexed two ways.
 *
 * <p>Spring hands over whatever {@link PaymentProvider} beans exist. A
 * deployment without PayPal credentials has no PayPal bean and therefore no
 * PayPal option on the checkout, with nothing to switch off by hand.
 *
 * <p>Two providers claiming the same {@link PaymentMethod} is a configuration
 * mistake that would otherwise resolve itself arbitrarily -- one of them takes
 * the money and which one depends on bean ordering. It fails at startup
 * instead.
 */
@Component
public class PaymentProviders {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviders.class);

    private final Map<PaymentMethod, PaymentProvider> byMethod = new LinkedHashMap<>();
    private final Map<String, PaymentProvider> byId = new LinkedHashMap<>();

    public PaymentProviders(List<PaymentProvider> providers) {
        for (PaymentProvider provider : providers) {
            PaymentProvider clash = byMethod.put(provider.method(), provider);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two payment providers claim " + provider.method() + ": "
                                + clash.id() + " and " + provider.id()
                                + ". One of them would take the money and which one is arbitrary.");
            }
            byId.put(provider.id(), provider);
        }
        log.info("Payment providers registered: {}",
                byId.isEmpty() ? "none (no method can take money)" : byId.keySet());
    }

    /** The provider for a method, when this deployment can actually charge it. */
    public Optional<PaymentProvider> forMethod(PaymentMethod method) {
        return Optional.ofNullable(byMethod.get(method))
                .filter(PaymentProvider::isConfigured);
    }

    /** The provider named in a return, cancel or webhook URL. */
    public Optional<PaymentProvider> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<PaymentProvider> all() {
        return List.copyOf(byId.values());
    }
}
