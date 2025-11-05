package com.example.backorders.controller;

import com.example.backorders.model.Order;
import com.example.backorders.service.OrderService;
import com.example.backorders.exceptions.OrderStateException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
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


    // ======================
    // HU-4: HISTORIAL CON FILTROS Y PAGINACIÓN
    // ruta completa con filtros
    @GetMapping("/user/{userId}/completa")
    public ResponseEntity<?> getOrdersByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        try {
        
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
           }

            // Valida que el usuario autenticado sea el mismo
            String username = principal.getName();
            if (!username.equals(userId.toString())) {
                return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver estas órdenes"));
            }

            // Llama al servicio
        var ordersPage = orderService.getOrdersByUserId(userId.toString(), status, fechaInicio, page, size);
        return ResponseEntity.ok(ordersPage);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error interno del servidor", "detalle", e.getMessage()));
        }
   }


    // ======================
    // GET /orders/user/{userId} - historial del usuario autenticado
    // ruta simple sin filtros
    @GetMapping("/user/{userId}/simple")
    public ResponseEntity<?> getOrdersByUser(@PathVariable String userId, Principal principal) {
        // Seguridad: solo el dueño ve sus órdenes
        if (principal == null || !principal.getName().equals(userId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No autorizado"));
        }

        return ResponseEntity.ok(orderService.getOrdersByUserId(userId));
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

