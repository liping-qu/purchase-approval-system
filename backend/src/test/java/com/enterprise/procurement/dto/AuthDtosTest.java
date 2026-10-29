package com.enterprise.procurement.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 防回归：`/api/auth/me` 的响应体里绝不能出现口令字段。
 *
 * <p>这类漏洞曾经真实存在——接口直接把数据库投影对象序列化出去了。用一条断言把它钉死：
 * 以后谁往 VO 里加字段，只要带上 password 相关命名，这里就会红。</p>
 */
class AuthDtosTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("CurrentUser 序列化结果不含任何口令字段")
    void currentUserHasNoPassword() throws Exception {
        var vo = new AuthDtos.CurrentUser(1L, "admin", "系统管理员", 5L, "采购部",
                Set.of("ADMIN"), Set.of("user:manage"));

        String body = json.writeValueAsString(vo);

        assertThat(body).doesNotContain("password");
        assertThat(body).contains("\"username\":\"admin\"");
        assertThat(body).contains("\"departmentName\":\"采购部\"");
    }
}
