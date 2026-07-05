// ========== 考生相关枚举 ==========

/** 科类 */
export enum SubjectCategory {
  LIBERAL_ARTS = 'LIBERAL_ARTS',
  SCIENCE = 'SCIENCE',
  COMPREHENSIVE = 'COMPREHENSIVE',
}

/** 考生类别 */
export enum CandidateType {
  CITY_FRESH = 'CITY_FRESH',
  CITY_PREVIOUS = 'CITY_PREVIOUS',
  RURAL_FRESH = 'RURAL_FRESH',
  RURAL_PREVIOUS = 'RURAL_PREVIOUS',
}

/** 志愿表状态 */
export enum FormStatus {
  DRAFT = 'DRAFT',
  SUBMITTED = 'SUBMITTED',
  CONFIRMED = 'CONFIRMED',
}

/** 录取批次 */
export enum AdmissionBatch {
  EARLY = 'EARLY',
  FIRST_BATCH = 'FIRST_BATCH',
  SECOND_BATCH = 'SECOND_BATCH',
  VOCATIONAL = 'VOCATIONAL',
}

// ========== 推荐相关枚举 ==========

/** 冲刺/稳妥/保底 */
export enum RecommendLevel {
  REACH = 'REACH',
  MATCH = 'MATCH',
  SAFETY = 'SAFETY',
}

/** 风险等级 */
export enum RiskLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
}

// ========== 院校相关枚举 ==========

/** 办学类型 */
export enum SchoolType {
  PUBLIC = 'PUBLIC',
  PRIVATE = 'PRIVATE',
  SINO_FOREIGN = 'SINO_FOREIGN',
}

/** 院校层次 */
export enum SchoolLevel {
  C9 = 'C9',
  PROJECT_985 = 'PROJECT_985',
  PROJECT_211 = 'PROJECT_211',
  DOUBLE_FIRST_CLASS = 'DOUBLE_FIRST_CLASS',
  PROVINCIAL_KEY = 'PROVINCIAL_KEY',
  ORDINARY = 'ORDINARY',
}

// ========== 数据管理枚举 ==========

/** 导入类型 */
export enum ImportType {
  SCORE_RANK = 'SCORE_RANK',
  ADMISSION_PLAN = 'ADMISSION_PLAN',
  HISTORY_DATA = 'HISTORY_DATA',
}

/** 导入状态 */
export enum ImportStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  PARTIAL = 'PARTIAL',
}

/** 数据版本状态 */
export enum VersionStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  ARCHIVED = 'ARCHIVED',
}

// ========== 用户相关枚举 ==========

/** 用户角色 */
export enum UserRole {
  CANDIDATE = 'CANDIDATE',
  ADMIN = 'ADMIN',
  SUPER_ADMIN = 'SUPER_ADMIN',
}

/** 审核状态 */
export enum AuditAction {
  LOGIN = 'LOGIN',
  LOGOUT = 'LOGOUT',
  CREATE = 'CREATE',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE',
  IMPORT = 'IMPORT',
  PUBLISH = 'PUBLISH',
  ARCHIVE = 'ARCHIVE',
  EXPORT = 'EXPORT',
}
