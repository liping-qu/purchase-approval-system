package com.enterprise.procurement.exception;

/**
 * 并发冲突：请求本身合法，但<b>状态已被别人抢先改变</b>，本次操作无法完成。
 *
 * <p>和 {@link IllegalArgumentException}（参数写错了，客户端的锅）的边界要分清：</p>
 * <ul>
 *   <li>两个负责人同时审批同一张单，CAS 影响行数为 0 的那个 —— 冲突；</li>
 *   <li>重复点击入库，订单已经是 DELIVERED —— 冲突；</li>
 *   <li>并发下扣库存抢输了（{@code quantity >= n} 不满足）—— 冲突。</li>
 * </ul>
 * <p>这三类原来统统抛 {@code IllegalStateException}，被全局处理器兜成 <b>400</b>，
 * 前端和监控把"被人抢先"误读成"请求不合法"。正确语义是 <b>409 Conflict</b>：
 * 告诉客户端"你基于的是旧状态，刷新后重试或放弃"，而不是"你的请求格式有问题"。</p>
 */
public class StateConflictException extends BusinessException {

    public StateConflictException(String message) {
        super(ErrorCode.STATE_CONFLICT, message);
    }

    /** 用于更精确的冲突码（订单已入库、申请已下单、库存不足、幂等冲突）。 */
    public StateConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
