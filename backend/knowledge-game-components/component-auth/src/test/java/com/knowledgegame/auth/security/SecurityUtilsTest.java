package com.knowledgegame.auth.security;

import com.knowledgegame.core.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityUtils 单元测试
 */
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("getCurrentUserId")
    class GetCurrentUserId {

        @Test
        @DisplayName("认证上下文中返回正确的 userId")
        void authenticated_returnsUserId() {
            setupAuthentication(42L, "testuser", "USER");

            assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("未认证时抛出 BusinessException")
        void notAuthenticated_throws() {
            SecurityContextHolder.clearContext();

            assertThatThrownBy(SecurityUtils::getCurrentUserId)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未认证");
        }
    }

    @Nested
    @DisplayName("getCurrentUsername")
    class GetCurrentUsername {

        @Test
        @DisplayName("认证上下文中返回正确的用户名")
        void authenticated_returnsUsername() {
            setupAuthentication(1L, "admin", "ADMIN");

            assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("未认证时抛出 BusinessException")
        void notAuthenticated_throws() {
            SecurityContextHolder.clearContext();

            assertThatThrownBy(SecurityUtils::getCurrentUsername)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未认证");
        }
    }

    @Nested
    @DisplayName("getCurrentUserRole")
    class GetCurrentUserRole {

        @Test
        @DisplayName("认证上下文中返回正确的角色")
        void authenticated_returnsRole() {
            setupAuthentication(1L, "admin", "ADMIN");

            assertThat(SecurityUtils.getCurrentUserRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("未认证时抛出 BusinessException")
        void notAuthenticated_throws() {
            SecurityContextHolder.clearContext();

            assertThatThrownBy(SecurityUtils::getCurrentUserRole)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未认证");
        }
    }

    private void setupAuthentication(Long userId, String username, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(username);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
