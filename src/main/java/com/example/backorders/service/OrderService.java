package com.example.backorders.service;

import com.example.backorders.model.Order;
import com.example.backorders.model.OrderItem;
import com.example.backorders.model.Product;
import com.example.backorders.Repositories.OrderRepositorio;
import com.example.backorders.Repositories.ProductRepositorio;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
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
}

