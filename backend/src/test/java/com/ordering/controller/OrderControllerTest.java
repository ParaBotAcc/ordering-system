package com.ordering.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordering.dto.OrderConfirmRequest;
import com.ordering.dto.OrderRequest;
import com.ordering.entity.Order;
import com.ordering.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** UC-CTL: Controller 接口测试 */
@WebMvcTest({OrderController.class, MenuController.class})
@DisplayName("Controller 接口测试")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderService orderService;

    private Order mockOrder(String status) throws Exception {
        var o = new Order();
        o.setOrderNo("ORD" + System.currentTimeMillis());
        o.setTableNo("A01");
        o.setStatus(status);
        o.setItems(objectMapper.writeValueAsString(List.of(
                new OrderRequest.OrderItem() {{
                    setName("测试菜");
                    setPrice(1000);
                    setQuantity(1);
                    setSubtotal(1000);
                }}
        )));
        o.setTotalPrice(1000);
        return o;
    }

    @Nested
    @DisplayName("UC-CTL-01~02: 菜单接口")
    class MenuEndpoint {

        @Test
        @DisplayName("UC-CTL-01: GET /api/menu → 200")
        void getMenu_shouldReturn200() throws Exception {
            var resp = new com.ordering.dto.MenuResponse();
            resp.setCategories(java.util.List.of());
            resp.setItems(java.util.List.of());
            when(orderService.getMenu()).thenReturn(resp);

            mockMvc.perform(get("/api/menu"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories").isArray())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("UC-CTL-02: GET /api/menu/search?keyword=牛 → 200")
        void searchMenu_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/menu/search").param("keyword", "牛"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("UC-CTL-03~04: 下单接口")
    class OrderCreateEndpoint {

        @Test
        @DisplayName("UC-CTL-03: POST /api/order → 201")
        void createOrder_shouldReturn201() throws Exception {
            var req = new OrderRequest();
            req.setTableNo("A01");
            var item = new OrderRequest.OrderItem();
            item.setName("酸菜鱼");
            item.setPrice(3800);
            item.setQuantity(1);
            item.setSubtotal(3800);
            req.setItems(List.of(item));

            when(orderService.createOrder(any())).thenReturn(mockOrder("CREATED"));

            mockMvc.perform(post("/api/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        @DisplayName("UC-CTL-04: POST /api/order 空购物车 → 400")
        void createOrder_emptyCart_shouldReturn400() throws Exception {
            var req = new OrderRequest();
            req.setTableNo("A01");
            req.setItems(List.of());

            mockMvc.perform(post("/api/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("UC-CTL-05~07: 订单查询接口")
    class OrderQueryEndpoint {

        @Test
        @DisplayName("UC-CTL-05: GET /api/order/{orderNo} → 200")
        void getOrder_shouldReturn200() throws Exception {
            var o = mockOrder("CREATED");
            o.setOrderNo("ORD123");
            when(orderService.getOrder("ORD123")).thenReturn(o);

            mockMvc.perform(get("/api/order/ORD123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNo").value("ORD123"));
        }

        @Test
        @DisplayName("UC-CTL-06: GET /api/order/table/{tableNo} → 200")
        void getByTable_shouldReturn200() throws Exception {
            when(orderService.getOrdersByTable("A01")).thenReturn(List.of(mockOrder("CREATED")));

            mockMvc.perform(get("/api/order/table/A01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("UC-CTL-07: GET /api/order/list → 200")
        void listAll_shouldReturn200() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(mockOrder("CREATED")));

            mockMvc.perform(get("/api/order/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("UC-CTL-08~10: 订单操作接口")
    class OrderActionEndpoint {

        @Test
        @DisplayName("UC-CTL-08: PUT /api/order/{orderNo}/status → 200")
        void updateStatus_shouldReturn200() throws Exception {
            when(orderService.updateStatus("ORD123", "PREPARING")).thenReturn(mockOrder("PREPARING"));

            mockMvc.perform(put("/api/order/ORD123/status").param("status", "PREPARING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PREPARING"));
        }

        @Test
        @DisplayName("UC-CTL-09: POST /api/order/confirm → 200")
        void confirmPickup_shouldReturn200() throws Exception {
            var req = new OrderConfirmRequest();
            req.setOrderNo("ORD123");
            req.setConfirmedItemNames(List.of("酸菜鱼"));

            when(orderService.confirmPickup(any())).thenReturn(mockOrder("CONFIRMED"));

            mockMvc.perform(post("/api/order/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("UC-CTL-10: POST /api/order/merge → 200")
        void mergeOrders_shouldReturn200() throws Exception {
            when(orderService.mergeOrders(List.of("ORD1", "ORD2"))).thenReturn(mockOrder("CREATED"));

            mockMvc.perform(post("/api/order/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of("ORD1", "ORD2"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }
    }
}
