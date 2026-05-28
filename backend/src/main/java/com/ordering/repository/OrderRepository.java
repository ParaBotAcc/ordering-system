package com.ordering.repository;

import com.ordering.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);
    List<Order> findByTableNoOrderByCreatedAtDesc(String tableNo);
    List<Order> findByStatusOrderByCreatedAtDesc(String status);
    List<Order> findAllByOrderByCreatedAtDesc();
}
