package com.ordering.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "feishu")
public class FeishuConfig {
    private String appId;
    private String appSecret;
    private Bitable bitable = new Bitable();

    @Data
    public static class Bitable {
        private String tableAppToken;
        private String menuTableId;
        private String orderTableId;
        private String tableTableId;
    }
}
