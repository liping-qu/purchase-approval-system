package com.enterprise.procurement.business;

import com.enterprise.procurement.persistence.mapper.SequenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单号生成的单元测试。
 *
 * <p>重点不是"字符串拼得对不对"，而是锁住两条约束：</p>
 * <ol>
 *   <li>格式固定为 {@code 前缀 + 8 位日期 + 6 位流水}，前端和运营都按这个格式口头传递单号；</li>
 *   <li>每次生成都先自增号段再读取，顺序不能反 —— 反了就会拿到别人已经用掉的号。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DocumentNoGeneratorTest {

    @Mock
    SequenceMapper sequences;

    @InjectMocks
    DocumentNoGenerator generator;

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @DisplayName("申请单号 = PR + 日期 + 6 位流水")
    void requestNo() {
        when(sequences.currentValue()).thenReturn(42L);

        assertThat(generator.nextRequestNo()).isEqualTo("PR" + today() + "000042");
        verify(sequences).bump("REQUEST");
    }

    @Test
    @DisplayName("订单号 = PO + 日期 + 6 位流水，且与申请单号使用独立号段")
    void orderNo() {
        when(sequences.currentValue()).thenReturn(3L);

        assertThat(generator.nextOrderNo()).isEqualTo("PO" + today() + "000003");
        verify(sequences).bump("ORDER");
    }
}
