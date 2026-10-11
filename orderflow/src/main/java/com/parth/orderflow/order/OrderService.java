package com.parth.orderflow.order;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderCreationTransaction creation;

    public OrderService(OrderRepository orders, OrderCreationTransaction creation) {
        this.orders = orders;
        this.creation = creation;
    }

    public OrderResponse create(String idempotencyKey, CreateOrderRequest request) {
        return create(idempotencyKey, request, false);
    }

    public OrderResponse create(String idempotencyKey, CreateOrderRequest request, boolean demoMode) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 255) {
            throw new InvalidIdempotencyKeyException();
        }
        String requestHash = OrderRequestFingerprint.from(request);
        return orders.findByIdempotencyKeyAndDemoMode(idempotencyKey, demoMode)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> createOrReplayRace(idempotencyKey, requestHash, request, demoMode));
    }

    private OrderResponse createOrReplayRace(String key, String requestHash, CreateOrderRequest request,
                                             boolean demoMode) {
        try {
            return creation.create(key, requestHash, request, demoMode);
        } catch (DataIntegrityViolationException exception) {
            return orders.findByIdempotencyKeyAndDemoMode(key, demoMode)
                    .map(existing -> replay(existing, requestHash))
                    .orElseThrow(() -> exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            return orders.findByIdempotencyKeyAndDemoMode(key, demoMode)
                    .map(existing -> replay(existing, requestHash))
                    .orElseThrow(ConcurrentOrderUpdateException::new);
        }
    }

    private OrderResponse replay(Order existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }
        return OrderResponse.from(existing);
    }
}
