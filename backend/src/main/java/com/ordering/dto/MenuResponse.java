package com.ordering.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MenuResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Category> categories;
    private List<MenuItem> items;

    @Data
    public static class Category implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int count;
    }

    @Data
    public static class MenuItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String name;
        private String category;
        private String imageUrl;
        private Integer price;        // 分，前端除以100
        private Integer stock;
        private String spec;
        private String description;
        private Boolean available;
    }
}

@Data
class OrderResponse {
    private String orderNo;
    private String tableNo;
    private List<OrderRequest.OrderItem> items;
    private Integer totalPrice;
    private String status;
    private LocalDateTime createdAt;
}
