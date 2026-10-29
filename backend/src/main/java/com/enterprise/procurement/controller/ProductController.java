package com.enterprise.procurement.controller;

import com.enterprise.procurement.dto.ApiResponse;
import com.enterprise.procurement.persistence.mapper.CatalogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品目录查询。
 *
 * <p>说明：原实现从内存 Map 返回 2 条硬编码商品。现改为查询 {@code sys_product}，
 * 只返回 {@code active = 1} 的可采购商品，供"新建采购申请"的商品选择器使用。</p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogMapper catalog;

    public ProductController(CatalogMapper catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(catalog.products());
    }
}
