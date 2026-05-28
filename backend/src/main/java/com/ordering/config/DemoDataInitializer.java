package com.ordering.config;

import com.ordering.entity.Menu;
import com.ordering.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final MenuRepository menuRepository;

    @Override
    public void run(String... args) {
        if (menuRepository.count() > 0) {
            log.info("数据库已有数据，跳过初始化");
            return;
        }

        log.info("初始化 Demo 菜品数据...");

        menuRepository.save(create("招牌酸菜鱼", "招牌推荐", 3800, -1, "约2人份，可选辣度"));
        menuRepository.save(create("水煮牛肉", "招牌推荐", 4200, -1, "约1-2人份，麻辣鲜香"));
        menuRepository.save(create("糖醋里脊", "招牌推荐", 3200, -1, "约1人份，酸甜可口"));

        menuRepository.save(create("白米饭", "主食", 200, -1, "碗"));
        menuRepository.save(create("蛋炒饭", "主食", 1200, -1, "份"));
        menuRepository.save(create("扬州炒饭", "主食", 1500, -1, "份"));
        menuRepository.save(create("牛肉面", "主食", 1800, -1, "碗"));

        menuRepository.save(create("凉拌黄瓜", "小食", 800, -1, "份"));
        menuRepository.save(create("口水鸡", "小食", 1600, -1, "份"));
        menuRepository.save(create("炸春卷", "小食", 1000, -1, "6个装"));
        menuRepository.save(create("花生米", "小食", 500, -1, "份"));

        menuRepository.save(create("可乐", "饮品", 400, -1, "罐"));
        menuRepository.save(create("雪碧", "饮品", 400, -1, "罐"));
        menuRepository.save(create("冰红茶", "饮品", 500, -1, "瓶"));
        menuRepository.save(create("矿泉水", "饮品", 200, -1, "瓶"));
        menuRepository.save(create("酸梅汤", "饮品", 800, -1, "杯"));

        log.info("Demo 数据初始化完成，共 {} 道菜品", menuRepository.count());
    }

    private Menu create(String name, String category, int price, int stock, String spec) {
        Menu m = new Menu();
        m.setName(name);
        m.setCategory(category);
        m.setPrice(price);
        m.setStock(stock);
        m.setSpec(spec);
        m.setStatus(1);
        return m;
    }
}
