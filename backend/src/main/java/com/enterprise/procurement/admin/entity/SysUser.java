package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_user")
public class SysUser { @TableId(type=IdType.AUTO) private Long id; private String username; private String password; private String nickname; private String phone; private String email; private Long departmentId; private Integer status; /** 令牌版本：改密/强制下线时 +1，使此前签发的 JWT 全部失效 */ private Integer tokenVersion; private LocalDateTime createTime; private LocalDateTime updateTime; }
