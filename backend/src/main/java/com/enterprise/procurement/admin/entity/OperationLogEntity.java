package com.enterprise.procurement.admin.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_operation_log")
public class OperationLogEntity { @TableId(type=IdType.AUTO) private Long id; private Long operatorId; private String module; private String action; private String requestUri; private String ip; private String result; private LocalDateTime createTime; }
