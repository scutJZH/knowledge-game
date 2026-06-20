package com.knowledgegame.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateMemberRoleRequest {

    @NotBlank
    @Pattern(regexp = "ADMIN|MEMBER", message = "角色仅允许 ADMIN 或 MEMBER")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
