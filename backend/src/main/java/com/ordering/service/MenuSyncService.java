package com.ordering.service;

import com.ordering.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuSyncService {

    private final MenuRepository menuRepository;
    private final OrderService orderService;

    /**
     * 定时刷新菜单缓存（每分钟）
     * 生产环境：从飞书同步菜品变更
     * Demo 模式：仅刷新缓存
     */
    @Scheduled(fixedRate = 60_000)
    public void syncMenuCache() {
        orderService.refreshMenuCache();
        log.debug("菜单缓存已刷新");
    }
}
