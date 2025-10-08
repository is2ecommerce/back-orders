package com.example.backorders.dto;

import java.util.Date;
import java.util.List;

public class OrderSummaryDTO {
    private Long orderId;
    private Date fechaCreacion;
    private String estado;
    private Double total;
    private List<OrderItemDTO> items;

    public OrderSummaryDTO() {}

    public OrderSummaryDTO(Long orderId, Date fechaCreacion, String estado, Double total, List<OrderItemDTO> items) {
        this.orderId = orderId;
        this.fechaCreacion = fechaCreacion;
        this.estado = estado;
        this.total = total;
        this.items = items;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Date getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Date fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }
}
