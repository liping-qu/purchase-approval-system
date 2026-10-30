package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.persistence.entity.SupplierEntity;
import com.enterprise.procurement.persistence.mapper.SupplierMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.NoSuchElementException;

/**
 * 供应商维护。
 *
 * <p>删除是软删（{@code active=0}），历史订单还要靠 id 关联出供应商名称，
 * 所以「停用」与「物理删除」不是一回事。</p>
 *
 * <p>改造前这里的三个问题让软删形同虚设：</p>
 * <ul>
 *   <li>{@code SupplierForm.toEntity} 无条件写死 {@code active=1}，
 *       于是<b>编辑一次就把已停用的供应商复活了</b>——软删等于没删；</li>
 *   <li>{@code SupplierEntity} 字段上还带了 {@code active=1} 的初始值，
 *       哪怕不显式赋值，updateById 也会把 1 写回去（MyBatis-Plus 只忽略 null）；</li>
 *   <li>列表不过滤 active，删除后刷新还能看见；删除不存在的 id 也返回成功。</li>
 * </ul>
 *
 * <p>现在：编辑已停用的供应商直接拒绝（要恢复请走 {@code /restore}），
 * 恢复必须是显式动作，而不是编辑的副作用。</p>
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierMapper mapper;

    public SupplierController(SupplierMapper mapper) {
        this.mapper = mapper;
    }

    /** 只返回在用供应商：停用的对下单没有意义，历史单据另有 id 关联。 */
    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(mapper.selectList(new QueryWrapper<SupplierEntity>()
                .eq("active", 1)
                .orderByAsc("id")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')")
    public ApiResponse<?> create(@Valid @RequestBody SupplierForm form) {
        SupplierEntity s = new SupplierEntity();
        form.applyTo(s);
        s.setActive(1);   // 只有新建才由这里决定初始状态
        mapper.insert(s);
        return ApiResponse.ok(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PURCHASER')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody SupplierForm form) {
        SupplierEntity s = require(id);
        if (!Integer.valueOf(1).equals(s.getActive())) {
            throw new IllegalStateException("供应商已停用，请先恢复后再编辑");
        }
        form.applyTo(s);  // 不碰 active，停用状态不会被编辑顺手改掉
        mapper.updateById(s);
        return ApiResponse.ok(s);
    }

    /** 软删：置 active=0。重复删除是幂等的，但删不存在的 id 要报 404。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        SupplierEntity s = require(id);
        if (!Integer.valueOf(0).equals(s.getActive())) {
            s.setActive(0);
            mapper.updateById(s);
        }
        return ApiResponse.ok(true);
    }

    /** 显式恢复：软删的配套动作，没有它停用就等于不可逆。 */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> restore(@PathVariable Long id) {
        SupplierEntity s = require(id);
        if (!Integer.valueOf(1).equals(s.getActive())) {
            s.setActive(1);
            mapper.updateById(s);
        }
        return ApiResponse.ok(s);
    }

    private SupplierEntity require(Long id) {
        SupplierEntity s = mapper.selectById(id);
        if (s == null) {
            throw new NoSuchElementException("供应商不存在");
        }
        return s;
    }

    public record SupplierForm(@NotBlank String name, String code, String contact, String phone, String address) {

        /** 只回填用户可编辑的字段；active、id 由调用方决定。 */
        void applyTo(SupplierEntity s) {
            s.setName(name);
            s.setCode(code);
            s.setContact(contact);
            s.setPhone(phone);
            s.setAddress(address);
        }
    }
}
