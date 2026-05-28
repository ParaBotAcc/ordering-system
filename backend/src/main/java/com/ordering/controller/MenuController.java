package com.ordering.controller;

import com.ordering.dto.MenuResponse;
import com.ordering.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单接口")
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "获取完整菜单（含分类）")
    public MenuResponse getMenu() {
        return orderService.getMenu();
    }

    @GetMapping("/search")
    @Operation(summary = "搜索菜品")
    public List<MenuResponse.MenuItem> search(@RequestParam String keyword) {
        return orderService.searchMenu(keyword);
    }
}
