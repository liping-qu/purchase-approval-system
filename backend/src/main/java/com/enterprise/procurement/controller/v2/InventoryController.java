package com.enterprise.procurement.controller.v2;

import com.enterprise.procurement.aspect.OperationLog;
import com.enterprise.procurement.business.PurchaseBusinessService;
import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.dto.InventoryDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 库存查询与手工调整。
 *
 * <p>改造前这里只有两个 GET，而前端库存页的"入库/出库"按钮调的是
 * {@code POST /api/inventory/{id}/adjust} —— 接口根本不存在，点了就是 404
 * （或落进兜底变 500）。"页面看起来能用、实际没有"是最容易在演示现场翻车的一类问题。</p>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final PurchaseBusinessService service;

    public InventoryController(PurchaseBusinessService service) {
        this.service = service;
    }

    /** 列表带商品名/分类/单位/最低库存/更新时间，前端表格直接可用。 */
    @GetMapping("/stock")
    public ApiResponse<?> stock() {
        return ApiResponse.ok(service.stock());
    }

    @GetMapping("/transactions")
    public ApiResponse<?> transactions() {
        return ApiResponse.ok(service.transactions());
    }

    /**
     * 手工入库/出库：{@code quantity} 正数入库、负数出库。
     *
     * <p>路径变量用 productId 而不是库存行的自增 id —— inventory_stock 的主键就是
     * product_id，没有第二套 id；改造前前端传 {@code row.id}（undefined），
     * 就算接口存在也会打到 {@code /inventory/undefined/adjust}。</p>
     */
    @PostMapping("/{productId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @OperationLog(value = "手工调整库存", module = "库存")
    public ApiResponse<?> adjust(@PathVariable Long productId,
                                 @Valid @RequestBody(required = false) InventoryDtos.Adjust body,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                 Principal principal) {
        Integer quantity = body == null ? null : body.quantity();
        service.adjustStock(productId, quantity, service.userId(principal.getName()), idemKey);
        return ApiResponse.ok(quantity > 0 ? "入库成功" : "出库成功", null);
    }
}
