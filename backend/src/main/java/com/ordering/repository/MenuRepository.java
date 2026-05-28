package com.ordering.repository;

import com.ordering.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByStatusOrderByCategoryAscIdAsc(Integer status);
    List<Menu> findByCategoryAndStatus(String category, Integer status);
    List<Menu> findByNameContainingAndStatus(String name, Integer status);
}
