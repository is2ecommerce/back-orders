package com.example.backorders.model;

import jakarta.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    public static final String STATUS_PENDING = "pendiente";
    public static final String STATUS_COMPLETED = "completada";
    public static final String STATUS_PAID = "pagada";
    public static final String STATUS_CANCELLED = "cancelada";
    public static final String STATUS_IN_DELIVERY = "en camino";
    public static final String STATUS_PENDING_DELIVERY = "pendiente de entrega";
    public static final String STATUS_DELIVERED = "entregada";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String status;
    private String userId; // id del usuario propietario de la orden (desde Keycloak / JWT)
    private Double totalAmount;
    private Date createdAt = new Date();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItem> items;

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
