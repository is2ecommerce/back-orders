package com.example.proyecto.controller;

import com.example.proyecto.model.Order;
import com.example.proyecto.service.OrderService;
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
}
