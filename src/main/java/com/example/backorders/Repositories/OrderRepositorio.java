package com.example.backorders.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backorders.model.Order;

public interface OrderRepositorio extends JpaRepository<Order, Long> {
	java.util.List<com.example.backorders.model.Order> findByUserIdOrderByCreatedAtDesc(String userId);
}
