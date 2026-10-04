package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_permission")
public class Permission { @TableId(type=IdType.AUTO) private Long id; private String code; private String name; private LocalDateTime createTime; }
