package com.ordering.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class OrderConfirmRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotEmpty(message = "请勾选确认菜品")
    private List<String> confirmedItemNames;    // 顾客确认取走的菜品名称列表
}
