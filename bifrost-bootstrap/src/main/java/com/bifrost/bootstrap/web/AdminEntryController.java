package com.bifrost.bootstrap.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理端入口跳转：{@code /} 与 {@code /admin} → {@code /admin/}（302）。
 *
 * <p>让使用者只需要记一个地址（{@code http://<host>:18080/}）。跳转本身与
 * 管理端产物是否存在无关：没有打包前端产物的构建（例如直接 {@code ./mvnw package}
 * 后 `java -jar`）访问 {@code /} 会得到"302 → /admin/ → 404"，这是预期行为，
 * 见操作手册 05 与 FAQ。</p>
 *
 * <p>302 而非 301：避免浏览器把跳转永久缓存住，妨碍将来调整管理端路径。</p>
 */
@Controller
public class AdminEntryController {

    @GetMapping({"/", "/admin"})
    public String adminEntry() {
        return "redirect:/admin/";
    }
}
