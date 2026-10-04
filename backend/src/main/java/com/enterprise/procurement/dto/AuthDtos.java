package com.enterprise.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import com.enterprise.procurement.model.Enums;

public final class AuthDtos { private AuthDtos(){}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record LoginResponse(String token,Long userId,String username,String displayName,Set<Enums.UserRole> roles){}
}
