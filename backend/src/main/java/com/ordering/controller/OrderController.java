package com.ordering.controller;

import com.ordering.dto.OrderConfirmRequest;
import com.ordering.dto.OrderRequest;
import com.ordering.entity.Order;
import com.ordering.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单接口")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "提交订单")
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "查询订单详情")
    public ResponseEntity<Order> getOrder(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.getOrder(orderNo));
    }

    @GetMapping("/table/{tableNo}")
    @Operation(summary = "按桌号查询订单")
    public ResponseEntity<List<Order>> getByTable(@PathVariable String tableNo) {
        return ResponseEntity.ok(orderService.getOrdersByTable(tableNo));
    }

    @GetMapping("/list")
    @Operation(summary = "所有订单列表")
    public ResponseEntity<List<Order>> listAll() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PutMapping("/{orderNo}/status")
    @Operation(summary = "更新订单状态")
    public ResponseEntity<Order> updateStatus(
            @PathVariable String orderNo,
            @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateStatus(orderNo, status));
    }

    @PostMapping("/confirm")
    @Operation(summary = "顾客取餐确认")
    public ResponseEntity<Order> confirmPickup(@Valid @RequestBody OrderConfirmRequest request) {
        return ResponseEntity.ok(orderService.confirmPickup(request));
    }

    @PostMapping("/merge")
    @Operation(summary = "合并订单")
    public ResponseEntity<Order> mergeOrders(@RequestBody List<String> orderNos) {
        return ResponseEntity.ok(orderService.mergeOrders(orderNos));
    }
}
