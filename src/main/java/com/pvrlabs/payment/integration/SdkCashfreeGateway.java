package com.pvrlabs.payment.integration;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SdkCashfreeGateway implements CashfreeGateway {

    private final Cashfree cashfree;

    @Override
    public OrderEntity createOrder(CreateOrderRequest request) throws ApiException {
        ApiResponse<OrderEntity> response = cashfree.PGCreateOrder(request, null, null, null);
        return response != null ? response.getData() : null;
    }

    @Override
    public OrderEntity fetchOrder(String orderId) throws ApiException {
        ApiResponse<OrderEntity> response = cashfree.PGFetchOrder(orderId, null, null, null);
        return response != null ? response.getData() : null;
    }

    @Override
    public List<PaymentEntity> fetchPayments(String orderId) throws ApiException {
        ApiResponse<List<PaymentEntity>> response = cashfree.PGOrderFetchPayments(orderId, null, null, null);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }

    @Override
    public void verifyWebhookSignature(String signature, String rawBody, String timestamp) throws Exception {
        cashfree.PGVerifyWebhookSignature(signature, rawBody, timestamp);
    }
}
