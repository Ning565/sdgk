-- ============================================================
-- V5: Seed Data
-- System dictionaries, year configs, admin roles,
-- and permissions. Minimal but functional seed data.
-- MySQL 8.4 | charset=utf8mb4 | engine=InnoDB
-- ============================================================

-- -----------------------------------------------------------
-- year_config — 年度配置 (2023-2026)
-- -----------------------------------------------------------
INSERT INTO year_config (year, score_min, score_max, subject_options_json, volunteer_limit, is_open, remark) VALUES
(2023, 0, 750, '{"combos":[{"name":"物化生","index":1},{"name":"物化地","index":2},{"name":"物化政","index":3},{"name":"物生地","index":4},{"name":"物生政","index":5},{"name":"物地政","index":6},{"name":"史地政","index":7},{"name":"史生化","index":8},{"name":"史地化","index":9}]}', 96, 0, '2023年高考(已结束)'),
(2024, 0, 750, '{"combos":[{"name":"物化生","index":1},{"name":"物化地","index":2},{"name":"物化政","index":3},{"name":"物生地","index":4},{"name":"物生政","index":5},{"name":"物地政","index":6},{"name":"史地政","index":7},{"name":"史生化","index":8},{"name":"史地化","index":9}]}', 96, 0, '2024年高考(已结束)'),
(2025, 0, 750, '{"combos":[{"name":"物化生","index":1},{"name":"物化地","index":2},{"name":"物化政","index":3},{"name":"物生地","index":4},{"name":"物生政","index":5},{"name":"物地政","index":6},{"name":"史地政","index":7},{"name":"史生化","index":8},{"name":"史地化","index":9}]}', 96, 1, '2025年高考(进行中)'),
(2026, 0, 750, '{"combos":[{"name":"物化生","index":1},{"name":"物化地","index":2},{"name":"物化政","index":3},{"name":"物生地","index":4},{"name":"物生政","index":5},{"name":"物地政","index":6},{"name":"史地政","index":7},{"name":"史生化","index":8},{"name":"史地化","index":9}]}', 96, 0, '2026年高考(准备中)');

-- -----------------------------------------------------------
-- admin_role — 角色
-- -----------------------------------------------------------
INSERT INTO admin_role (code, name, description) VALUES
('SUPER_ADMIN', '超级管理员', '系统最高权限,管理所有功能和数据'),
('OPERATOR',    '运营',       '日常运营权限,数据导入/发布/查看'),
('READONLY',    '只读',       '只读权限,仅可查看数据');

-- -----------------------------------------------------------
-- admin_permission — 权限定义
-- -----------------------------------------------------------
INSERT INTO admin_permission (code, name, resource_type, resource_path, action) VALUES
-- 院校管理
('school:create',   '创建院校',   'SCHOOL', '/admin/schools',      'CREATE'),
('school:read',     '查看院校',   'SCHOOL', '/admin/schools',      'READ'),
('school:update',   '修改院校',   'SCHOOL', '/admin/schools/*',    'UPDATE'),
('school:delete',   '删除院校',   'SCHOOL', '/admin/schools/*',    'DELETE'),

-- 专业管理
('major:create',    '创建专业',   'MAJOR',  '/admin/majors',       'CREATE'),
('major:read',      '查看专业',   'MAJOR',  '/admin/majors',       'READ'),
('major:update',    '修改专业',   'MAJOR',  '/admin/majors/*',     'UPDATE'),

-- 招生计划管理
('plan:read',       '查看招生计划', 'PLAN',  '/admin/plans',        'READ'),
('plan:import',     '导入招生计划', 'PLAN',  '/admin/plans/import',  'CREATE'),
('plan:publish',    '发布招生计划', 'PLAN',  '/admin/plans/publish', 'UPDATE'),

-- 录取历史管理
('history:read',    '查看录取历史', 'HISTORY', '/admin/histories',   'READ'),
('history:import',  '导入录取历史', 'HISTORY', '/admin/histories/import', 'CREATE'),
('history:publish', '发布录取历史', 'HISTORY', '/admin/histories/publish', 'UPDATE'),

-- 一分一段表管理
('score_rank:read',    '查看一分一段',    'SCORE_RANK', '/admin/score-ranks',    'READ'),
('score_rank:import',  '导入一分一段',    'SCORE_RANK', '/admin/score-ranks/import', 'CREATE'),
('score_rank:publish', '发布一分一段',    'SCORE_RANK', '/admin/score-ranks/publish', 'UPDATE'),

-- 用户管理
('user:read',       '查看用户',   'USER',   '/admin/users',        'READ'),
('user:update',     '修改用户',   'USER',   '/admin/users/*',      'UPDATE'),
('user:disable',    '禁用用户',   'USER',   '/admin/users/*/disable', 'UPDATE'),

-- 志愿管理
('volunteer:read',  '查看志愿',   'VOLUNTEER', '/admin/volunteers', 'READ'),

-- 审计日志
('audit:read',      '查看审计日志', 'AUDIT', '/admin/audit-logs',  'READ'),

-- 系统管理
('system:manage',   '系统管理',   'SYSTEM',  '/admin/system',       'MANAGE'),
('version:manage',  '版本管理',   'VERSION', '/admin/versions',     'MANAGE'),

-- 导出
('export:data',     '导出数据',   'EXPORT',  '/admin/export',       'EXPORT');

-- -----------------------------------------------------------
-- admin_role_permission — 角色-权限关联
-- -----------------------------------------------------------

-- 超级管理员: 所有权限
INSERT INTO admin_role_permission (role_id, permission_id)
SELECT (SELECT id FROM admin_role WHERE code = 'SUPER_ADMIN'), id
FROM admin_permission;

-- 运营角色: 数据管理 + 查看权限
INSERT INTO admin_role_permission (role_id, permission_id)
SELECT (SELECT id FROM admin_role WHERE code = 'OPERATOR'), id
FROM admin_permission
WHERE code IN (
    'school:create', 'school:read', 'school:update',
    'major:create', 'major:read', 'major:update',
    'plan:read', 'plan:import', 'plan:publish',
    'history:read', 'history:import', 'history:publish',
    'score_rank:read', 'score_rank:import', 'score_rank:publish',
    'user:read',
    'volunteer:read',
    'audit:read',
    'export:data'
);

-- 只读角色: 仅查看权限
INSERT INTO admin_role_permission (role_id, permission_id)
SELECT (SELECT id FROM admin_role WHERE code = 'READONLY'), id
FROM admin_permission
WHERE code IN (
    'school:read',
    'major:read',
    'plan:read',
    'history:read',
    'score_rank:read',
    'volunteer:read'
);

-- -----------------------------------------------------------
-- Seed admin user (password: admin123, BCrypt hashed)
-- In production, change this immediately!
-- -----------------------------------------------------------
INSERT INTO admin_user (username, password_hash, real_name, status) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 1);

-- Assign SUPER_ADMIN role to the seed admin user
INSERT INTO admin_user_role (user_id, role_id)
SELECT (SELECT id FROM admin_user WHERE username = 'admin'),
       (SELECT id FROM admin_role WHERE code = 'SUPER_ADMIN');
