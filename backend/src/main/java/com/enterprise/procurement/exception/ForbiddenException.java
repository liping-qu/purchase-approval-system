package com.enterprise.procurement.exception;

/**
 * 已登录但无权做这件事：不是管理角色、不是申请人本人、跨部门审批。
 *
 * <p>这类拒绝原来抛 {@code IllegalStateException}，被兜成 <b>400</b>，
 * 语义上完全错了——请求格式没问题，是"你没这个权限"，应为 <b>403</b>。
 * 区分开之后前端可以做正确的事：400 提示改输入，403 提示找管理员要权限。</p>
 *
 * <p>注意与"越权读别人的单"的区别：那种情况故意返回 <b>404</b>，
 * 因为 403 等于承认"这条记录确实存在"，可以被用来枚举 id。</p>
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
