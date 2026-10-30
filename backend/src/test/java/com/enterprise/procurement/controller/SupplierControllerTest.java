package com.enterprise.procurement.controller;

import com.enterprise.procurement.persistence.entity.SupplierEntity;
import com.enterprise.procurement.persistence.mapper.SupplierMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 供应商软删：删除必须真的"删掉"，编辑不能顺手"复活"。
 */
class SupplierControllerTest {

    private final SupplierMapper mapper = mock(SupplierMapper.class);
    private final SupplierController controller = new SupplierController(mapper);

    private static SupplierEntity supplier(Long id, int active) {
        SupplierEntity s = new SupplierEntity();
        s.setId(id);
        s.setName("华夏办公设备有限公司");
        s.setCode("SUP-001");
        s.setActive(active);
        return s;
    }

    private static SupplierController.SupplierForm form(String name) {
        return new SupplierController.SupplierForm(name, "SUP-001", "张三", "13800000000", "北京市");
    }

    @Test
    @DisplayName("新建：active 由 create 显式置 1（实体不再有默认值兜底）")
    void createAlwaysActive() {
        when(mapper.insert(any(SupplierEntity.class))).thenReturn(1);

        controller.create(form("优品供应链有限公司"));

        ArgumentCaptor<SupplierEntity> captor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getActive());
        assertEquals("优品供应链有限公司", captor.getValue().getName());
        assertNull(captor.getValue().getId(), "id 交给数据库自增，不能由表单带进来");
    }

    @Test
    @DisplayName("编辑在用供应商：字段更新，active 保持 1 不被覆盖")
    void updateKeepsActive() {
        when(mapper.selectById(1L)).thenReturn(supplier(1L, 1));

        controller.update(1L, form("华夏办公设备（新）"));

        ArgumentCaptor<SupplierEntity> captor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("华夏办公设备（新）", captor.getValue().getName());
        assertEquals(1, captor.getValue().getActive());
    }

    @Test
    @DisplayName("改造前的核心 bug：编辑已停用供应商会把它复活 —— 现在直接拒绝")
    void updateInactiveSupplierIsRejected() {
        when(mapper.selectById(2L)).thenReturn(supplier(2L, 0));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> controller.update(2L, form("改名试试")));

        assertTrue(ex.getMessage().contains("已停用"));
        verify(mapper, never()).updateById(any(SupplierEntity.class));
    }

    @Test
    @DisplayName("编辑不存在的供应商：404 而不是假装成功")
    void updateMissingSupplier() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(NoSuchElementException.class, () -> controller.update(99L, form("x")));
        verify(mapper, never()).updateById(any(SupplierEntity.class));
    }

    @Test
    @DisplayName("删除：只置 active=0，不动其他字段；重复删除不再写库")
    void deleteIsSoftAndIdempotent() {
        when(mapper.selectById(1L)).thenReturn(supplier(1L, 1));
        controller.delete(1L);

        ArgumentCaptor<SupplierEntity> captor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getActive());
        assertEquals("华夏办公设备有限公司", captor.getValue().getName(), "软删不能顺手清空业务字段");

        // 再删一次：状态已是 0，不应重复写库
        when(mapper.selectById(1L)).thenReturn(supplier(1L, 0));
        controller.delete(1L);
        verify(mapper, times(1)).updateById(any(SupplierEntity.class));
    }

    @Test
    @DisplayName("删除不存在的 id：404（改造前无论存不存在都返回 true）")
    void deleteMissingSupplier() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(NoSuchElementException.class, () -> controller.delete(99L));
    }

    @Test
    @DisplayName("恢复：把 active 从 0 改回 1，是唯一能复活供应商的动作")
    void restoreBringsSupplierBack() {
        when(mapper.selectById(2L)).thenReturn(supplier(2L, 0));
        controller.restore(2L);

        ArgumentCaptor<SupplierEntity> captor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getActive());
    }

    @Test
    @DisplayName("主键策略必须是数据库自增：雪花 id 是 19 位，超出前端 Number 安全范围会直接丢掉精度")
    void idIsAutoIncrement() throws NoSuchFieldException {
        var tableId = SupplierEntity.class.getDeclaredField("id")
                .getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class);
        assertNotNull(tableId, "@TableId 必须存在，否则 MyBatis-Plus 不认识主键");
        assertEquals(com.baomidou.mybatisplus.annotation.IdType.AUTO, tableId.type());
    }

    @Test
    @DisplayName("列表只查 active=1：删除后刷新列表不应再出现")
    @SuppressWarnings("unchecked")
    void listFiltersInactive() {
        when(mapper.selectList(any())).thenReturn(List.of(supplier(1L, 1)));

        controller.list();

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SupplierEntity>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("active"), "必须带 active 条件，实际：" + sql);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1), "active 过滤值应为 1");
    }
}
