package com.example.backorders.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backorders.model.Product;

public interface ProductRepositorio extends JpaRepository<Product, Long> {
}
