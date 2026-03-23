package com.payments.routing;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.Payment;
import com.payments.routing.provider.RazorpayAdapter;
import com.payments.routing.provider.StripeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class RoutingEngineTest {

    @Mock
    private StripeAdapter stripeAdapter;

    @Mock
    private RazorpayAdapter razorpayAdapter;

    private RoutingEngine routingEngine;

    @BeforeEach
    public void setup() {
        routingEngine = new RoutingEngine(stripeAdapter, razorpayAdapter);
    }

    @Test
    public void testDynamicRoutingToStripe() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        PaymentRequest req = new PaymentRequest();
        
        when(stripeAdapter.getProviderName()).thenReturn("STRIPE");
        when(razorpayAdapter.getProviderName()).thenReturn("RAZORPAY");
        
        PaymentResponse strResponse = PaymentResponse.builder().provider("STRIPE").build();
        when(stripeAdapter.charge(any())).thenReturn(strResponse);
        
        PaymentResponse response = routingEngine.routeAndProcess(payment, req);
        assertEquals("STRIPE", response.getProvider());
    }
}
