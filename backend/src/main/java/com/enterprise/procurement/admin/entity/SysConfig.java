package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_config")
public class SysConfig { @TableId(type=IdType.AUTO) private Long id; private String configKey; private String configValue; private String description; private LocalDateTime createTime; private LocalDateTime updateTime; }
