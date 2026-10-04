package com.enterprise.procurement.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.enterprise.procurement.persistence.entity.ApprovalRecordEntity; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordEntity> { @Select("SELECT * FROM approval_record WHERE request_id=#{requestId} ORDER BY created_at DESC") List<ApprovalRecordEntity> byRequest(@Param("requestId") Long id); }
