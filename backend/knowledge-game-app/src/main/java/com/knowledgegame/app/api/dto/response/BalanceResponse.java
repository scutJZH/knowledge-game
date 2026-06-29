package com.knowledgegame.app.api.dto.response;

/**
 * 当前积分余额响应（轻量读）
 */
public class BalanceResponse {

    private Long groupId;
    private Long userId;
    private int balance;

    public BalanceResponse() {}

    public BalanceResponse(Long groupId, Long userId, int balance) {
        this.groupId = groupId;
        this.userId = userId;
        this.balance = balance;
    }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
}
