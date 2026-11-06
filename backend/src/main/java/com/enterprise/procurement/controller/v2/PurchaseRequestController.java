package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.aspect.OperationLog;
import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.PurchaseRequestDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 采购申请。
 *
 * <p>读接口返回的是"展示态"（申请人姓名、部门名、金额、时间都已就绪），
 * 不再是实体镜像——字段与前端表格同名，前端不做改名，也就没有"列全空白"的错位空间。</p>
 */
@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {

    private final PurchaseBusinessService service;

    public PurchaseRequestController(PurchaseBusinessService service) {
        this.service = service;
    }

    /** 我的申请。{@code keyword} 匹配编号/事由/申请人，{@code status} 精确过滤。 */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String status,
                               Principal principal) {
        return ApiResponse.ok(service.list(principal.getName(), keyword, status));
    }

    /** 详情（含明细）。可见性由 service 判定，越权读取返回 404（不暴露记录是否存在）。 */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.detail(id, principal.getName()));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<?> items(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.requestItems(id, principal.getName()));
    }

    /**
     * 审批留痕。
     *
     * <p>{@code approval_record} 一直在写，但改造前<b>没有任何接口读它</b>：
     * 页面上看不到谁批的、什么时候批的、批注写了什么，出问题只能翻数据库。
     * 可见性与申请详情一致（复用同一套归属判断）。</p>
     */
    @GetMapping("/{id}/approvals")
    public ApiResponse<?> approvals(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.approvalHistory(id, principal.getName()));
    }

    @PostMapping
    @OperationLog(value = "创建采购申请", module = "采购申请")
    public ApiResponse<?> create(@Valid @RequestBody PurchaseRequestDtos.Create dto,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                 Principal principal) {
        var created = service.createDraft(principal.getName(), dto, idemKey);
        // submit=true 时事务里已经提交，提示语必须跟着状态走，否则用户以为只是存了草稿
        boolean submitted = "PENDING".equals(created.getStatus());
        return ApiResponse.ok(submitted ? "已提交，等待审批" : "草稿已创建", created);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody PurchaseRequestDtos.Update dto,
                                 Principal principal) {
        return ApiResponse.ok(service.updateDraft(id, principal.getName(), dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id, Principal principal) {
        service.deleteDraft(id, principal.getName());
        return ApiResponse.ok("已删除", null);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(value = "提交采购申请", module = "采购申请")
    public ApiResponse<?> submit(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.submit(id, principal.getName()));
    }

    @PostMapping("/{id}/withdraw")
    @OperationLog(value = "撤回采购申请", module = "采购申请")
    public ApiResponse<?> withdraw(@PathVariable Long id, Principal principal) {
        return ApiResponse.ok(service.withdraw(id, principal.getName()));
    }
}
