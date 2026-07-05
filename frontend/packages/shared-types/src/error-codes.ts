export const ErrorCodes = {
  // 通用错误
  SUCCESS: 0,
  UNKNOWN_ERROR: 10000,
  PARAM_ERROR: 1001,
  INVALID_PARAM: 10001,
  DATA_NOT_FOUND: 1003,
  UNAUTHORIZED: 2000,
  FORBIDDEN: 10004,
  RATE_LIMIT: 10005,

  // 认证错误
  AUTH_WRONG_PASSWORD: 20001,
  AUTH_USER_NOT_FOUND: 20002,
  AUTH_TOKEN_EXPIRED: 20003,
  AUTH_TOKEN_INVALID: 20004,
  AUTH_USER_DISABLED: 20005,

  // 考生错误
  CANDIDATE_NOT_FOUND: 30001,
  CANDIDATE_DUPLICATE: 30002,
  CANDIDATE_SCORE_INVALID: 30003,

  // 推荐错误
  RECOMMEND_NO_RESULT: 40001,
  RECOMMEND_INSUFFICIENT_DATA: 40002,

  // 志愿表错误
  FORM_NOT_FOUND: 50001,
  FORM_FULL: 50002,
  FORM_LOCKED: 50003,
  FORM_DUPLICATE_SCHOOL: 50004,

  // 数据导入错误
  IMPORT_INVALID_FORMAT: 60001,
  IMPORT_COLUMN_MISMATCH: 60002,
  IMPORT_DATA_CONFLICT: 60003,
} as const;

export const ErrorMessages: Record<number, string> = {
  [ErrorCodes.SUCCESS]: '操作成功',
  [ErrorCodes.UNKNOWN_ERROR]: '未知错误',
  [ErrorCodes.PARAM_ERROR]: '参数错误',
  [ErrorCodes.INVALID_PARAM]: '参数无效',
  [ErrorCodes.DATA_NOT_FOUND]: '数据不存在',
  [ErrorCodes.UNAUTHORIZED]: '未授权访问',
  [ErrorCodes.FORBIDDEN]: '无权访问',
  [ErrorCodes.RATE_LIMIT]: '请求过于频繁',

  [ErrorCodes.AUTH_WRONG_PASSWORD]: '密码错误',
  [ErrorCodes.AUTH_USER_NOT_FOUND]: '用户不存在',
  [ErrorCodes.AUTH_TOKEN_EXPIRED]: '登录已过期',
  [ErrorCodes.AUTH_TOKEN_INVALID]: '无效的登录凭证',
  [ErrorCodes.AUTH_USER_DISABLED]: '用户已被禁用',

  [ErrorCodes.CANDIDATE_NOT_FOUND]: '考生信息不存在',
  [ErrorCodes.CANDIDATE_DUPLICATE]: '考生信息重复',
  [ErrorCodes.CANDIDATE_SCORE_INVALID]: '考生成绩无效',

  [ErrorCodes.RECOMMEND_NO_RESULT]: '暂无推荐结果',
  [ErrorCodes.RECOMMEND_INSUFFICIENT_DATA]: '数据不足，无法生成推荐',

  [ErrorCodes.FORM_NOT_FOUND]: '志愿表不存在',
  [ErrorCodes.FORM_FULL]: '志愿表已满',
  [ErrorCodes.FORM_LOCKED]: '志愿表已锁定',
  [ErrorCodes.FORM_DUPLICATE_SCHOOL]: '志愿表中存在重复院校',

  [ErrorCodes.IMPORT_INVALID_FORMAT]: '导入文件格式无效',
  [ErrorCodes.IMPORT_COLUMN_MISMATCH]: '导入文件列不匹配',
  [ErrorCodes.IMPORT_DATA_CONFLICT]: '导入数据存在冲突',
};
