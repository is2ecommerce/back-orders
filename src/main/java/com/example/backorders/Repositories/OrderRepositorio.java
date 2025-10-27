package com.example.backorders.Repositories;

import com.example.backorders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepositorio extends JpaRepository<Order, Long> {

    // Consulta flexible: acepta status/start/end nulos
    @Query("SELECT o FROM Order o " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND (:start IS NULL OR o.createdAt >= :start) " +
           "AND (:end IS NULL OR o.createdAt <= :end) " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByFilter(@Param("status") String status,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    java.util.List<com.example.backorders.model.Order> findByUserIdOrderByCreatedAtDesc(String userId);
}
