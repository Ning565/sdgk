import type {
  SubjectCategory,
  CandidateType,
  FormStatus,
  AdmissionBatch,
  RecommendLevel,
  RiskLevel,
  SchoolType,
  SchoolLevel,
  ImportType,
  ImportStatus,
  VersionStatus,
  UserRole,
  AuditAction,
} from './enums';

// ========== 通用类型 ==========

export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: number;
}

// ========== 考生DTO ==========

export interface CandidateProfile {
  id?: number;
  userId?: number;
  year: number;
  score?: number;
  rank?: number;
  rankSource?: 'AUTO' | 'MANUAL';
  subjects: string[];
  subjectComboIndex?: number;
  educationLevel?: 'UNDERGRADUATE' | 'VOCATIONAL' | 'UNLIMITED';
  preferredRegions?: string[];
  preferredMajors?: string[];
  excludedMajors?: string[];
  tuitionMax?: number;
  schoolNature?: 'PUBLIC' | 'PRIVATE' | 'UNLIMITED';
  acceptJointProgram?: boolean;
}

// ========== 院校DTO ==========

export interface SchoolDTO {
  id: string;
  name: string;
  code: string;
  province: string;
  city: string;
  type: SchoolType;
  level: SchoolLevel;
  isDoubleFirstClass: boolean;
  website: string;
  contactPhone: string;
  description: string;
  logoUrl: string;
}

export interface SchoolDetailDTO extends SchoolDTO {
  historicalScores: HistoricalScoreDTO[];
  currentPlans: AdmissionPlanDTO[];
}

export interface HistoricalScoreDTO {
  year: number;
  minScore: number;
  avgScore: number;
  maxScore: number;
  minRank: number;
  batch: AdmissionBatch;
  subjectCategory: SubjectCategory;
}

export interface AdmissionPlanDTO {
  id: string;
  schoolId: string;
  schoolName: string;
  majorName: string;
  majorCode: string;
  planCount: number;
  batch: AdmissionBatch;
  subjectCategory: SubjectCategory;
  tuition: number;
  duration: number;
  year: number;
}

// ========== 推荐DTO ==========

export interface RecommendationDTO {
  planId: number;
  schoolId: number;
  schoolName: string;
  schoolCode: string;
  majorName: string;
  majorCode?: string;
  majorCategory?: string;
  majorSubcategory?: string;
  enrollmentType?: string;
  educationLevel?: string;
  planCount: number;
  tuition?: number;
  duration?: number;
  planStatus?: string;
  subjectRequirementText?: string;
  province?: string;
  city?: string;
  schoolType?: string;
  schoolTag?: string;
  probability?: number;
  label?: string;
  planChange?: string;
  lastYearMinRank?: number;
  predictedRank?: number;
}

export interface SchoolRecommendationGroupDTO {
  schoolId: number;
  schoolName: string;
  schoolCode: string;
  province?: string;
  city?: string;
  schoolType?: string;
  schoolTag?: string;
  eligiblePlanCount: number;
  minProbability?: number;
  maxProbability?: number;
  plans: RecommendationDTO[];
}

export interface RecommendationResponseDTO {
  schoolGroups: SchoolRecommendationGroupDTO[];
  totalPlans: number;
  totalSchools: number;
  planDataVersion?: string;
  historyDataVersion?: string;
  modelVersion?: string;
  updatedAt?: string;
  traceId?: string;
}

export interface RecommendationRequest {
  year: number;
  educationLevel?: string;
  score?: number;
  rank?: number;
  subjects?: string[];
  subjectComboIndex?: number;
  keyword?: string;
  province?: string[];
  city?: string[];
  schoolType?: string[];
  schoolTag?: string[];
  majorCategory?: string[];
  majorSubcategory?: string[];
  enrollmentType?: string[];
  excludeMajorCategory?: string[];
  excludeMajorSubcategory?: string[];
  excludeSinoForeign?: boolean;
  excludeSchoolEnterprise?: boolean;
  tuitionMin?: number;
  tuitionMax?: number;
  planCountMin?: number;
  planCountMax?: number;
  probabilityMin?: number;
  probabilityMax?: number;
  label?: string;
  recommendationCount?: number;
  rushRatio?: number;
  stableRatio?: number;
  safeRatio?: number;
  rushProbabilityMin?: number;
  sortBy?: string;
  sortDir?: string;
  pageNo?: number;
  pageSize?: number;
}

// ========== 志愿表DTO ==========

export interface VolunteerFormDTO {
  id: string | number;
  userId?: string | number;
  candidateId?: string;
  year?: number;
  name: string;
  version?: number;
  itemCount?: number;
  status: FormStatus | 'ACTIVE' | 'ARCHIVED';
  totalChoices?: number;
  filledChoices?: number;
  createdAt: string;
  updatedAt: string;
}

export interface VolunteerFormDetailDTO extends VolunteerFormDTO {
  choices?: VolunteerChoiceDTO[];
  items?: VolunteerChoiceDTO[];
  stats?: { chongCount: number; wenCount: number; baoCount: number };
}

export interface VolunteerChoiceDTO {
  id: string | number;
  formId?: string | number;
  planId?: number;
  order?: number;
  sortOrder?: number;
  schoolId?: string | number;
  schoolCode?: string;
  schoolName: string;
  majorName: string;
  majorCode?: string;
  province?: string;
  city?: string;
  schoolType?: string;
  enrollmentType?: string;
  probability?: number;
  label?: string;
  planCount?: number;
  tuition?: number;
  subjectRequirementText?: string;
  planStatus?: string;
  lastYearMinRank?: number;
  predictedRank?: number;
  batch?: AdmissionBatch;
  isObeyAdjustment?: boolean;
  recommendLevel?: RecommendLevel;
  riskLevel?: RiskLevel;
  historicalMinScore?: number;
  historicalMinRank?: number;
  note?: string;
  addedAt?: string;
}

// ========== 数据管理DTO ==========

export interface DataImportDTO {
  id: string;
  batchId: string;
  type: ImportType;
  status: ImportStatus;
  fileName: string;
  totalRows: number;
  successRows: number;
  failedRows: number;
  errorDetailUrl: string;
  year: number;
  province: string;
  createdAt: string;
  createdBy: string;
  completedAt: string;
}

export interface DataVersionDTO {
  id: string;
  version: string;
  year: number;
  province: string;
  status: VersionStatus;
  description: string;
  createdAt: string;
  createdBy: string;
  publishedAt: string;
}

export interface DataLinkDTO {
  id: string;
  name: string;
  url: string;
  category: string;
  sortOrder: number;
  isActive: boolean;
  createdAt: string;
}

// ========== 审计日志DTO ==========

export interface AuditLogDTO {
  id: string;
  userId: string;
  username: string;
  action: AuditAction;
  resource: string;
  resourceId: string;
  detail: string;
  ip: string;
  traceId: string;
  createdAt: string;
}

// ========== 用户角色DTO ==========

export interface UserDTO {
  userId: number;
  username?: string;
  nickname: string;
  status: string;
  lastLoginAt?: string;
}

// ========== 认证DTO ==========

export interface LoginRequest {
  username: string;
  password: string;
}
