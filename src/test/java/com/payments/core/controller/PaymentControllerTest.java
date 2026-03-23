package com.payments.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.core.dto.PaymentMethod;
import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.PaymentStatus;
import com.payments.core.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreatePayment() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setIdempotencyKey("test-key");
        request.setAmount(5000L);
        request.setCurrency("USD");
        request.setCustomerId("cust_123");
        PaymentMethod method = new PaymentMethod();
        method.setType("card");
        method.setToken("tok_mastercard");
        request.setPaymentMethod(method);

        PaymentResponse response = PaymentResponse.builder()
                .paymentId("pay_123")
                .status(PaymentStatus.SUCCEEDED)
                .provider("STRIPE")
                .amount(5000L)
                .currency("USD")
                .build();

        Mockito.when(paymentService.processPayment(any())).thenReturn(response);

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.provider").value("STRIPE"));
    }
}
