package com.example.backorders.Repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.backorders.model.Order;
import java.util.List;
import java.util.Date;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ===========================
    // BÚSQUEDAS BÁSICAS
    // ===========================

    // Busca órdenes por userId ordenadas por fecha descendente
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    // Obtiene órdenes paginadas por userId
    Page<Order> findByUserId(String userId, Pageable pageable);

    // Obtiene órdenes paginadas por userId y estado (sin importar mayúsculas)
    Page<Order> findByUserIdAndStatusIgnoreCase(String userId, String status, Pageable pageable);

    // Obtiene órdenes paginadas por userId y fecha posterior
    Page<Order> findByUserIdAndCreatedAtAfter(String userId, Date fecha, Pageable pageable);

    // ===========================
    // CONSULTA COMBINADA PERSONALIZADA
    // ===========================

    @Query("""
        SELECT o FROM Order o
        WHERE o.userId = :userId
          AND (:status IS NULL OR LOWER(o.status) = LOWER(:status))
          AND (:fecha IS NULL OR o.createdAt >= :fecha)
    """)
    Page<Order> findByUserIdAndOptionalFilters(
        @Param("userId") String userId,
        @Param("status") String status,
        @Param("fecha") Date fecha,
        Pageable pageable
   );
}



