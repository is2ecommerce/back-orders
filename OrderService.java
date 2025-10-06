package com.example.proyecto.service;

import com.example.proyecto.model.Order;
import com.example.proyecto.model.OrderItem;
import com.example.proyecto.model.Product;
import com.example.proyecto.repository.OrderRepository;
import com.example.proyecto.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
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
                productRepository.save(product);
            }

            // Marcar orden cancelada
            order.setStatus("cancelled");
            orderRepository.save(order);
            return Optional.of(order);
        }

        return Optional.empty();
    }
}
