-- 小餐饮扫码点餐系统 - 数据库初始化脚本
-- 使用 utf8mb4 支持 emoji

CREATE TABLE IF NOT EXISTS `menu` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '菜品名称',
    `category`    VARCHAR(50)  NOT NULL COMMENT '分类：招牌推荐/主食/小食/饮品',
    `image_url`   VARCHAR(500) NULL     COMMENT '菜品图片URL',
    `price`       INT          NOT NULL COMMENT '单价，单位：分',
    `stock`       INT          NOT NULL DEFAULT -1 COMMENT '库存，-1=无限',
    `spec`        VARCHAR(100) NULL     COMMENT '规格，如"份/大份/小份"',
    `description` VARCHAR(200) NULL     COMMENT '简短描述',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

CREATE TABLE IF NOT EXISTS `orders` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `order_no`        VARCHAR(32)  NOT NULL UNIQUE COMMENT '订单号',
    `table_no`        VARCHAR(10)  NOT NULL COMMENT '桌号',
    `items`           JSON         NOT NULL COMMENT '菜品明细：[{name,spec,price,quantity,subtotal}]',
    `total_price`     INT          NOT NULL COMMENT '总价，单位：分',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PREPARING/PENDING_CONFIRM/CONFIRMED/CLOSED/VERIFIED',
    `confirm_detail`  JSON         NULL     COMMENT '取餐确认详情',
    `note`            VARCHAR(500) NULL     COMMENT '备注',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_table_no` (`table_no`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
