package com.ordering.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    @NotBlank(message = "桌号不能为空")
    private String tableNo;

    @NotEmpty(message = "至少选择一件商品")
    private List<OrderItem> items;

    private String note;

    @Data
    public static class OrderItem {
        @NotBlank(message = "菜品名称不能为空")
        private String name;

        private String spec;

        @Min(value = 1, message = "单价必须大于0")
        private Integer price;       // 分

        @Min(value = 1, message = "数量必须大于0")
        private Integer quantity;

        @Min(value = 1, message = "小计必须大于0")
        private Integer subtotal;    // 分
    }
}
