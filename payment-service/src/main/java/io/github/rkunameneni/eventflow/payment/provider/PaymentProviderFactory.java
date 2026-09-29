package io.github.rkunameneni.eventflow.payment.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Factory that resolves the configured {@link PaymentProvider} strategy by name. */
@Component
public class PaymentProviderFactory {

    private final Map<String, PaymentProvider> providersByName;

    public PaymentProviderFactory(List<PaymentProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentProvider::name, Function.identity()));
    }

    public PaymentProvider get(String name) {
        PaymentProvider provider = providersByName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown payment provider '" + name + "'; available: " + providersByName.keySet());
        }
        return provider;
    }
}
