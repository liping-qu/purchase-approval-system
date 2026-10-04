package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_user")
public class SysUser { @TableId(type=IdType.AUTO) private Long id; private String username; private String password; private String nickname; private String phone; private String email; private Long departmentId; private Integer status; private LocalDateTime createTime; private LocalDateTime updateTime; }
