package com.bifrost.adapter.kosync.controller;

import com.bifrost.adapter.kosync.KosyncAuthAttributes;
import com.bifrost.adapter.kosync.KosyncConstants;
import com.bifrost.adapter.kosync.KosyncException;
import com.bifrost.adapter.kosync.dto.AuthorizedResponse;
import com.bifrost.adapter.kosync.dto.CreateUserRequest;
import com.bifrost.adapter.kosync.dto.HealthResponse;
import com.bifrost.adapter.kosync.dto.ProgressGetResponse;
import com.bifrost.adapter.kosync.dto.ProgressPutRequest;
import com.bifrost.adapter.kosync.dto.ProgressPutResponse;
import com.bifrost.adapter.kosync.dto.UserCreatedResponse;
import com.bifrost.core.book.sync.ReadingProgressService;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.core.book.sync.UnmatchedProgressScanTrigger;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.SyncAccount;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * KOSync 协议端点（M3-sync T2.4）。
 *
 * <h2>为什么所有方法都返回 {@link ResponseEntity} 且手工指定 Content-Type</h2>
 * 客户端每个请求都带 {@code Accept: application/vnd.koreader.v1+json}，而它<b>只能解析
 * {@code application/json}</b>（被 patch 过的 JSON 中间件正则不含 {@code +}，vendor 类型不解码）。
 * 若用 {@code @RequestMapping(produces = APPLICATION_JSON_VALUE)}，Spring 的内容协商会因为
 * Accept 与 produces 不兼容直接回 <b>406</b>（真实设备上表现为"注册/同步全部失败"）；若干脆不写
 * {@code produces}，Spring 又会把响应的 Content-Type 协商成客户端要的 vendor 类型，客户端照样解析不了。
 * 所以：<b>不声明 produces，改为显式指定响应 Content-Type = application/json</b>。
 * 集成测试 {@code KosyncProtocolIntegrationTest} 全程带真实 Accept 头，专门钉住这一点。
 *
 * <h2>其它契约要点</h2>
 * <ul>
 *   <li>注册成功只有 <b>201</b>，登录/上报只有 <b>200</b>；<b>永不返回 202</b>；</li>
 *   <li>认证失败 <b>401</b>（唯一不重试的码）；未知 document 返回 <b>200 {@code {}}</b> 而非 404；</li>
 *   <li>任何失败都带可读 {@code message}（注册/登录失败时客户端原样展示给用户）；</li>
 *   <li>端点轻量：上报只做 upsert + 即时匹配（一次 DB 查询）+ 设备计数，扫描在后台异步触发
 *       （客户端进度推送超时只有 2/5 秒）。</li>
 * </ul>
 *
 * <p>开关与安全链同源：{@code bifrost.kosync.enabled}（关闭后端点 404，而不是裸奔）。</p>
 */
@Slf4j
@RestController
@ConditionalOnProperty(name = "bifrost.kosync.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class KosyncController {

    private final SyncAccountService syncAccountService;
    private final ReadingProgressService progressService;
    private final UnmatchedProgressScanTrigger scanTrigger;
    private final BifrostProperties properties;

    /**
     * {@code POST /users/create} —— <b>幂等自助注册</b>（R2b）。
     *
     * <p>用户名与配置的同步账号一致且口令匹配 → 201（设备上点 Register 还是 Login 都能成功）；
     * 其它情况 402 + 可读 message，且<b>不泄露</b>账号是否存在。</p>
     */
    @PostMapping(KosyncConstants.PATH_USERS_CREATE)
    public ResponseEntity<UserCreatedResponse> createUser(@RequestBody(required = false) CreateUserRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw KosyncException.invalidField("username/password 不能为空");
        }
        String username = request.username().trim();
        if (username.contains(":") || username.length() > KosyncConstants.MAX_USERNAME_LENGTH) {
            throw KosyncException.invalidField("用户名不合法");
        }
        if (!properties.getKosync().isRegistrationEnabled()) {
            throw KosyncException.registrationDisabled();
        }
        SyncAccount account = syncAccountService.currentOrCreate();
        if (!account.getUsername().equals(username)) {
            // 与官方"已注册"语义一致：不区分"这个名字被占用"与"别的账号存在"
            throw KosyncException.conflict("用户名已被占用");
        }
        if (!syncAccountService.verify(username, request.password())) {
            throw KosyncException.conflict("同步账号口令不匹配");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserCreatedResponse(username));
    }

    /** {@code GET /users/auth} —— 凭据校验（客户端不读响应体，只看 200）。 */
    @GetMapping(KosyncConstants.PATH_USERS_AUTH)
    public ResponseEntity<AuthorizedResponse> authorize() {
        return json(AuthorizedResponse.ok());
    }

    /**
     * {@code PUT /syncs/progress} —— 上报进度（<b>无条件后写覆盖</b>）。
     *
     * <p>首次见到该文档指纹时：先做一次<b>即时匹配</b>（库里已有这本书就直接绑定），未命中才
     * 触发一次后台扫描（R5/C1，同一指纹只会触发一次）。</p>
     */
    @PutMapping(KosyncConstants.PATH_PROGRESS)
    public ResponseEntity<ProgressPutResponse> putProgress(@RequestBody(required = false) ProgressPutRequest request,
                                                           HttpServletRequest httpRequest) {
        Long accountId = syncAccountId(httpRequest);
        if (request == null) {
            throw KosyncException.invalidField("请求体不能为空");
        }
        String document = requireDocument(request.document());
        String progress = requireText(request.progress(), "progress");
        if (request.percentage() == null) {
            throw KosyncException.invalidField("percentage 不能为空");
        }
        String device = requireText(request.device(), "device");
        String deviceId = requireText(request.deviceId(), "device_id");

        ReadingProgressService.ProgressUpsert upsert = progressService.upsert(
                accountId, document, progress, request.percentage(), device, deviceId);

        if (upsert.firstAppearance() && !progressService.tryAutoMatch(upsert.progress().getId())) {
            scanTrigger.triggerAsync();
        }

        return json(new ProgressPutResponse(document, upsert.progress().getReportedAt().getEpochSecond()));
    }

    /**
     * {@code GET /syncs/progress/:document} —— 读取进度。
     *
     * <p>未知 document 返回 {@code 200 {}}：客户端据此提示"没找到进度"，404 会变成报错弹窗。</p>
     */
    @GetMapping(KosyncConstants.PATH_PROGRESS_DOCUMENT)
    public ResponseEntity<ProgressGetResponse> getProgress(@PathVariable("document") String document,
                                                           HttpServletRequest httpRequest) {
        Long accountId = syncAccountId(httpRequest);
        String doc = requireDocument(document);
        return json(progressService.find(accountId, doc)
                .map(ProgressGetResponse::of)
                .orElseGet(ProgressGetResponse::notFound));
    }

    /** {@code GET /healthcheck} —— 匿名探活。 */
    @GetMapping(KosyncConstants.PATH_HEALTHCHECK)
    public ResponseEntity<HealthResponse> health() {
        return json(HealthResponse.ok());
    }

    /** 显式 JSON 响应（绕开内容协商，见类注释）。 */
    private static <T> ResponseEntity<T> json(T body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** 账号 id 由认证过滤器写入；取不到说明安全链没生效 → 401 而不是 500。 */
    private Long syncAccountId(HttpServletRequest request) {
        Object value = request.getAttribute(KosyncAuthAttributes.ATTR_SYNC_ACCOUNT_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw KosyncException.unauthorized("未授权");
    }

    private static String requireDocument(String document) {
        if (isBlank(document)) {
            throw KosyncException.invalidField("document 不能为空");
        }
        if (document.contains(":")) {
            throw KosyncException.invalidField("document 不能包含 ':'");
        }
        if (document.length() > KosyncConstants.MAX_DOCUMENT_LENGTH) {
            throw KosyncException.invalidField("document 过长");
        }
        return document;
    }

    private static String requireText(String value, String field) {
        if (isBlank(value)) {
            throw KosyncException.invalidField(field + " 不能为空");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
