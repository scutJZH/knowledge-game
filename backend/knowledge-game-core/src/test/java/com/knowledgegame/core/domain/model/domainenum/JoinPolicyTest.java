package com.knowledgegame.core.domain.model.domainenum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinPolicyTest {

    @Test
    @DisplayName("枚举应有 OPEN 和 INVITE_ONLY 两个值")
    void enum_hasOpenAndInviteOnlyValues() {
        JoinPolicy[] values = JoinPolicy.values();
        assertEquals(2, values.length);
        assertEquals(JoinPolicy.OPEN, JoinPolicy.valueOf("OPEN"));
        assertEquals(JoinPolicy.INVITE_ONLY, JoinPolicy.valueOf("INVITE_ONLY"));
    }
}
