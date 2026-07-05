package com.example.admission.common;

/**
 * 高考志愿系统统一错误码。
 * 按模块分组：
 * <ul>
 *   <li>0: 成功</li>
 *   <li>1xxx: 通用 / 系统错误</li>
 *   <li>2xxx: 认证模块错误</li>
 *   <li>3xxx: 考生模块错误</li>
 *   <li>4xxx: 目录模块错误</li>
 *   <li>5xxx: 推荐模块错误</li>
 *   <li>6xxx: 预测模块错误</li>
 *   <li>7xxx: 志愿模块错误</li>
 *   <li>8xxx: 数据导入 / 导出错误</li>
 *   <li>9xxx: 管理 / 审计错误</li>
 * </ul>
 */
public enum ErrorCode {

    // --- 成功 ---
    SUCCESS(0, "操作成功"),

    // --- 通用 / 系统 (1xxx) ---
    SYSTEM_ERROR(1000, "系统内部错误"),
    PARAM_ERROR(1001, "参数错误"),
    PARAM_MISSING(1002, "缺少必要参数"),
    DATA_NOT_FOUND(1003, "数据不存在"),
    DATA_DUPLICATE(1004, "数据重复"),
    OPERATION_FAILED(1005, "操作失败"),
    RATE_LIMITED(1006, "请求过于频繁，请稍后再试"),
    SERVICE_UNAVAILABLE(1007, "服务暂不可用"),

    // --- 认证 (2xxx) ---
    UNAUTHORIZED(2000, "未登录或登录已过期"),
    FORBIDDEN(2001, "无权限访问"),
    TOKEN_INVALID(2002, "令牌无效"),
    TOKEN_EXPIRED(2003, "令牌已过期"),
    SMS_CODE_ERROR(2004, "短信验证码错误"),
    SMS_CODE_EXPIRED(2005, "短信验证码已过期"),
    SMS_SEND_FAILED(2006, "短信发送失败"),
    SMS_SEND_TOO_FREQUENT(2007, "短信发送过于频繁"),
    ACCOUNT_DISABLED(2008, "账号已被禁用"),
    ACCOUNT_NOT_EXIST(2009, "账号不存在"),
    PASSWORD_ERROR(2010, "密码错误"),
    ADMIN_NOT_EXIST(2011, "管理员不存在"),
    USERNAME_EXISTS(2012, "用户名已存在"),

    // --- 考生 (3xxx) ---
    CANDIDATE_NOT_FOUND(3000, "考生信息不存在"),
    CANDIDATE_PROFILE_INCOMPLETE(3001, "考生档案信息不完整"),
    SCORE_NOT_FOUND(3002, "考生成绩不存在"),
    PREFERENCE_SAVE_FAILED(3003, "偏好设置保存失败"),

    // --- 目录 (4xxx) ---
    SCHOOL_NOT_FOUND(4000, "学校不存在"),
    MAJOR_NOT_FOUND(4001, "专业不存在"),
    ENROLLMENT_PLAN_NOT_FOUND(4002, "招生计划不存在"),
    ENROLLMENT_HISTORY_NOT_FOUND(4003, "历史录取数据不存在"),

    // --- 推荐 (5xxx) ---
    RECOMMENDATION_FAILED(5000, "推荐计算失败"),
    FILTER_CONDITION_INVALID(5001, "筛选条件无效"),

    // --- 预测 (6xxx) ---
    PREDICTION_FAILED(6000, "预测算法调用失败"),
    PREDICTION_TIMEOUT(6001, "预测算法超时"),
    PREDICTION_CACHE_ERROR(6002, "预测缓存异常"),

    // --- 志愿 (7xxx) ---
    VOLUNTEER_FORM_NOT_FOUND(7000, "志愿表不存在"),
    VOLUNTEER_FORM_LOCKED(7001, "志愿表已锁定，无法修改"),
    VOLUNTEER_SLOT_FULL(7002, "志愿槽位已满"),
    VOLUNTEER_DUPLICATE(7003, "志愿重复"),
    VOLUNTEER_RISK_CHECK_FAILED(7004, "志愿风险评估未通过"),
    VOLUNTEER_SUBMIT_WINDOW_CLOSED(7005, "志愿填报时间窗口已关闭"),
    VOLUNTEER_FORM_VERSION_CONFLICT(7006, "志愿表版本冲突，请刷新后重试"),
    VOLUNTEER_ITEM_LIMIT_REACHED(7007, "志愿项已达上限"),
    VOLUNTEER_PLAN_ALREADY_ADDED(7008, "该计划已在志愿表中"),
    VOLUNTEER_PLAN_NOT_ACTIVE(7009, "该招生计划当前不可用"),

    // --- 数据导入 / 导出 (8xxx) ---
    IMPORT_FILE_EMPTY(8000, "导入文件为空"),
    IMPORT_FORMAT_ERROR(8001, "导入文件格式错误"),
    IMPORT_PARSE_ERROR(8002, "导入数据解析失败"),
    IMPORT_VALIDATION_ERROR(8003, "导入数据校验失败"),
    IMPORT_PUBLISH_FAILED(8004, "数据发布失败"),
    IMPORT_ROLLBACK_FAILED(8005, "数据回滚失败"),
    EXPORT_FAILED(8006, "导出失败"),
    EXPORT_DATA_EMPTY(8007, "导出数据为空"),
    EXPORT_CONFIRM_REQUIRED(8008, "志愿表中存在错误级别问题，请确认后导出"),
    EXPORT_FILE_NOT_FOUND(8009, "导出文件不存在"),

    // --- 管理 / 审计 (9xxx) ---
    AUDIT_LOG_FAILED(9000, "审计日志记录失败"),
    ADMIN_OPERATION_DENIED(9001, "管理员操作被拒绝"),
    YEAR_CONFIG_NOT_FOUND(9002, "年度配置不存在"),
    DICTIONARY_NOT_FOUND(9003, "字典项不存在"),
    ROLE_NOT_FOUND(9004, "角色不存在"),
    PERMISSION_DENIED(9005, "权限不足");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 根据数字错误码查找对应的 ErrorCode。
     */
    public static ErrorCode valueOf(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return SYSTEM_ERROR;
    }
}
