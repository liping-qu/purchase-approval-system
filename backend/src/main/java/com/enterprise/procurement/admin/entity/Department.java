package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_department")
public class Department { @TableId(type=IdType.AUTO) private Long id; private String name; private Long parentId; private Integer status; private LocalDateTime createTime; private LocalDateTime updateTime; }
