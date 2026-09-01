package com.pvrlabs.payment.integration;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.PaymentEntity;

import java.util.List;

/**
 * Thin wrapper around the Cashfree PG SDK so payment flows can be tested without live API calls.
 */
public interface CashfreeGateway {

    OrderEntity createOrder(CreateOrderRequest request) throws ApiException;

    OrderEntity fetchOrder(String orderId) throws ApiException;

    List<PaymentEntity> fetchPayments(String orderId) throws ApiException;

    void verifyWebhookSignature(String signature, String rawBody, String timestamp) throws Exception;
}
