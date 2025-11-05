package com.example.backorders.controller;

import com.example.backorders.model.Order;
import com.example.backorders.service.OrderService;
import com.example.backorders.exceptions.OrderStateException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
            @PathVariable String userId,
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
            if (!username.equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "No autorizado para ver estas órdenes"));
            }

            // Llama al servicio
            var ordersPage = orderService.getOrdersByUserId(userId, status, fechaInicio, page, size);
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

    @PatchMapping("/{orderId}/confirm-delivery")
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

    // ==============================================================
    // HU-5: VER RECIBO DE PAGO (PDF)
    // ==============================================================
    @GetMapping(value = "/{orderId}/receipt", produces = "application/pdf")
    public ResponseEntity<byte[]> getPaymentReceipt(
            @PathVariable Long orderId,
            Principal principal) {

        // 1. Validar autenticación (solo si hay seguridad activa)
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Buscar orden
        Optional<Order> orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

    Order order = orderOpt.get();

        // 3. Validar propietario y estado de pago
        if (!order.getUserId().toString().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!"pagada".equalsIgnoreCase(order.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("La orden no está pagada".getBytes());
       }

        // 4. Generar PDF con el servicio
        byte[] pdfBytes = orderService.generateReceiptPdf(order);

        // 5. Retornar respuesta con headers de descarga
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", "recibo_orden_" + orderId + ".pdf");
        headers.setContentType(MediaType.APPLICATION_PDF);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

}

