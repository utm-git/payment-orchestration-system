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
    public void testFallbackToRazorpay() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        PaymentRequest req = new PaymentRequest();
        
        PaymentResponse rzpResponse = PaymentResponse.builder().provider("RAZORPAY").build();
        when(razorpayAdapter.charge(any())).thenReturn(rzpResponse);
        
        PaymentResponse response = routingEngine.fallbackToRazorpay(payment, req, new RuntimeException("Timeout"));
        assertEquals("RAZORPAY", response.getProvider());
    }
}
