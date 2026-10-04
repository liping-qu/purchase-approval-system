package com.enterprise.procurement.dto;

import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List; import com.enterprise.procurement.model.Enums;

public final class ApplicationDtos { private ApplicationDtos(){}
    public record ItemRequest(@NotNull Long productId,@NotNull @Min(1) Integer quantity,@NotNull @DecimalMin("0.0") BigDecimal unitPrice){}
    public record CreateRequest(@NotBlank String reason,@NotEmpty List<@Valid ItemRequest> items){}
    public record ApprovalRequest(@NotNull Enums.ApprovalAction action,String comment){}
    public record OrderRequest(@NotNull Long supplierId){}
}
