package com.example.backorders.service;

import com.example.backorders.model.Order;
import com.example.backorders.model.OrderItem;
import com.example.backorders.model.Product;
import com.example.backorders.Repositories.OrderRepositorio;
import com.example.backorders.Repositories.ProductRepositorio;

import java.util.Optional;
public class OrderService {

    private final OrderRepositorio orderRepository;
    private final ProductRepositorio productRepository;

    public OrderService(OrderRepositorio orderRepository, ProductRepositorio productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> confirmDelivery(Long id, String userId) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (!orderOpt.isPresent()) {
            return Optional.empty();
        }

        Order order = orderOpt.get();
        
        // Validar que la orden pertenece al usuario
        if (!userId.equals(order.getUserId())) {
            return Optional.empty();
        }

        // Validar estado actual
        String currentState = order.getStatus();
        if (!"en camino".equals(currentState) && !"pendiente de entrega".equals(currentState)) {
            throw new OrderStateException(currentState, "en camino/pendiente de entrega");
        }

        // Actualizar estado
        order.setStatus("entregada");
        orderRepository.save(order);

        // Simular envío de notificación
        sendDeliveryConfirmationNotification(order);

        return Optional.of(order);
    }

    private void sendDeliveryConfirmationNotification(Order order) {
        // Simulación de envío de notificación
        System.out.println("Notificación enviada al usuario " + order.getUserId() + 
                          ": Su orden #" + order.getId() + " ha sido confirmada como entregada.");
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public java.util.List<com.example.backorders.dto.OrderSummaryDTO> getOrdersByUserId(String userId) {
        java.util.List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        java.util.List<com.example.backorders.dto.OrderSummaryDTO> result = new java.util.ArrayList<>();

        for (Order o : orders) {
            java.util.List<com.example.backorders.dto.OrderItemDTO> items = new java.util.ArrayList<>();
            if (o.getItems() != null) {
                for (com.example.backorders.model.OrderItem it : o.getItems()) {
                    Long pid = it.getProduct() != null ? it.getProduct().getId() : null;
                    items.add(new com.example.backorders.dto.OrderItemDTO(pid, it.getQuantity(), it.getPrice()));
                }
            }
            result.add(new com.example.backorders.dto.OrderSummaryDTO(o.getId(), o.getCreatedAt(), o.getStatus(), o.getTotalAmount(), items));
        }

        return result;
    }

    public Optional<Order> cancelOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();

            if (!order.getStatus().equals("pending")) {
                return Optional.empty();
            }

            // Reponer stock
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);  // <-- CORRECTO
            }

            // Marcar orden cancelada
            order.setStatus("cancelled");
            orderRepository.save(order);
            return Optional.of(order);
        }

        return Optional.empty();
    }

    // Nuevo: procesar pago y marcar como "pagada"
    public Optional<Order> payOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (!orderOpt.isPresent()) {
            return Optional.empty();
        }
        Order order = orderOpt.get();
        // Solo permitir pago si está en estado "pending"
        if (!"pending".equals(order.getStatus())) {
            return Optional.empty();
        }
        order.setStatus("pagada");
        orderRepository.save(order);
        return Optional.of(order);
    }
}

