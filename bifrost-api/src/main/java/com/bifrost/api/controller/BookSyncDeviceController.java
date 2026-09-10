package com.bifrost.api.controller;

import com.bifrost.api.dto.booksync.SyncDeviceView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.entity.SyncDevice;
import com.bifrost.domain.repo.SyncDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同步设备列表（M3-sync T3.5，R6）。
 *
 * <p><b>只读</b>：协议层没有会话，"踢设备"做不出真效果（Q5 决策）——要让所有设备重新登录，
 * 只能改同步口令。</p>
 */
@RestController
@RequestMapping("/api/book-sync/devices")
@RequiredArgsConstructor
public class BookSyncDeviceController {

    private final SyncAccountService syncAccountService;
    private final SyncDeviceRepository syncDeviceRepository;

    /** 设备列表（不分页，最近见到优先）。 */
    @GetMapping
    public ApiResponse<PageResult<SyncDeviceView>> list() {
        SyncAccount account = syncAccountService.current()
                .orElseThrow(() -> BizException.notFound("同步账号尚未初始化"));
        List<SyncDevice> devices = syncDeviceRepository.findBySyncAccountIdOrderByLastSeenAtDesc(account.getId());
        List<SyncDeviceView> items = devices.stream().map(SyncDeviceView::of).toList();
        return ApiResponse.ok(new PageResult<>(items.size(), items));
    }
}
