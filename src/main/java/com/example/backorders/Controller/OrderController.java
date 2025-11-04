package com.example.backorders.controller;

import com.example.backorders.model.Order;
import com.example.backorders.service.OrderService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ======================
    // TAREA 1: GET /orders/{orderId}
    // ======================
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderDetails(@PathVariable Long orderId) {
        Optional<Order> order = orderService.getOrderById(orderId);

        if (order.isPresent()) {
            return ResponseEntity.ok(order.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ======================
    // TAREA 2: PATCH /orders/{orderId}/cancel
    // ======================
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        Optional<Order> cancelledOrder = orderService.cancelOrder(orderId);

        if (cancelledOrder.isPresent()) {
            return ResponseEntity.ok(cancelledOrder.get());
        } else {
            return ResponseEntity.badRequest().body("No se puede cancelar esta orden");
        }
    }

    // PATCH /orders/{orderId}/status
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam String newStatus) {
        try {
            Order updatedOrder = orderService.updateOrderStatus(orderId, newStatus);
            return ResponseEntity.ok(updatedOrder);
        } catch (OrderStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /orders/{orderId}/payment
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<?> processPayment(
            @PathVariable Long orderId) {
        try {
            Order updatedOrder = orderService.processPayment(orderId);
            return ResponseEntity.ok(updatedOrder);
        } catch (OrderStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /orders/user/{userId} - historial del usuario autenticado
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getOrdersByUser(@PathVariable String userId, java.security.Principal principal) {
        // Seguridad simple: permitir solo si el principal corresponde al userId solicitado
        if (principal == null || !principal.getName().equals(userId)) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", "No autorizado"));
        }

        java.util.List<com.example.backorders.dto.OrderSummaryDTO> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<?> payOrder(@PathVariable Long id) {
        Optional<Order> existing = orderService.getOrderById(id);
        if (!existing.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Order> paid = orderService.payOrder(id);
        if (paid.isPresent()) {
            return ResponseEntity.ok(paid.get());
        } else {
            return ResponseEntity.status(409).body("No se pudo procesar el pago: estado de la orden no es 'pending'.");
        }
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> confirmDelivery(@PathVariable Long orderId, java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        try {
            Optional<Order> confirmed = orderService.confirmDelivery(orderId, principal.getName());
            if (!confirmed.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(confirmed.get());
        } catch (OrderStateException ex) {
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", ex.getMessage(),
                    "currentState", ex.getCurrentState(),
                    "requiredState", ex.getRequiredState()
                ));
        }
    }
}
