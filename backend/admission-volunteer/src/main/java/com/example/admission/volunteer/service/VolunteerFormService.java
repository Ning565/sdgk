package com.example.admission.volunteer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.volunteer.dto.*;
import com.example.admission.volunteer.entity.VolunteerForm;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteer.mapper.VolunteerFormMapper;
import com.example.admission.volunteer.mapper.VolunteerItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 志愿表核心服务.
 *
 * <p>管理志愿表的增删改查、志愿项添加/删除/排序/备注，
 * 使用乐观锁（version 字段）保证并发安全，写操作支持幂等（clientOperationId）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerFormService {

    private final VolunteerFormMapper volunteerFormMapper;
    private final VolunteerItemMapper volunteerItemMapper;
    private final AuthService authService;
    private final EnrollmentPlanService enrollmentPlanService;

    /** 每个用户每年最多志愿表数 */
    private static final int MAX_FORMS_PER_YEAR = 10;
    /** 志愿表默认容量（用户可修改，null = 不限） */
    private static final int DEFAULT_MAX_ITEMS = 96;
    /** 至少保留一份志愿表 */
    private static final int MIN_FORMS = 1;
    /** 幂等去重有效期（毫秒） */
    private static final long IDEMPOTENT_TTL_MS = 5 * 60 * 1000;

    /** 幂等操作缓存: operationKey -> timestamp */
    private final ConcurrentHashMap<String, Long> idempotentCache = new ConcurrentHashMap<>();

    // ==================== 志愿表管理 ====================

    /**
     * 列出当前用户指定年度的志愿表.
     */
    public List<VolunteerFormResponse> listForms(Integer year) {
        Long userId = authService.checkLogin().getId();
        List<VolunteerForm> forms = volunteerFormMapper.selectList(
                new LambdaQueryWrapper<VolunteerForm>()
                        .eq(VolunteerForm::getUserId, userId)
                        .eq(year != null, VolunteerForm::getYear, year)
                        .orderByDesc(VolunteerForm::getCreatedAt)
        );
        return forms.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 获取志愿表详情（含 items + 冲稳保统计）.
     */
    public VolunteerFormDetailResponse getFormDetail(Long formId) {
        VolunteerForm form = getFormWithOwnershipCheck(formId);
        List<VolunteerItem> items = volunteerItemMapper.selectList(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .orderByAsc(VolunteerItem::getSortOrder)
        );

        List<VolunteerFormDetailResponse.VolunteerItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        // 统计冲稳保
        int chong = 0, wen = 0, bao = 0;
        for (VolunteerFormDetailResponse.VolunteerItemResponse ir : itemResponses) {
            if ("冲".equals(ir.getLabel())) chong++;
            else if ("稳".equals(ir.getLabel())) wen++;
            else if ("保".equals(ir.getLabel())) bao++;
        }

        return VolunteerFormDetailResponse.builder()
                .id(form.getId())
                .userId(form.getUserId())
                .year(form.getYear())
                .name(form.getName())
                .version(form.getVersion())
                .itemCount(form.getItemCount())
                .maxItems(form.getMaxItems())
                .status(form.getStatus())
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .items(itemResponses)
                .stats(VolunteerFormDetailResponse.LabelStats.builder()
                        .chongCount(chong).wenCount(wen).baoCount(bao).build())
                .build();
    }

    /**
     * 创建志愿表.
     */
    @Transactional
    public VolunteerFormResponse createForm(Integer year, String name) {
        UserAccount user = authService.checkLogin();
        Long userId = user.getId();
        int formYear = year != null ? year : Year.now().getValue();

        // 检查数量上限
        long count = volunteerFormMapper.selectCount(
                new LambdaQueryWrapper<VolunteerForm>()
                        .eq(VolunteerForm::getUserId, userId)
                        .eq(VolunteerForm::getYear, formYear)
        );
        if (count >= MAX_FORMS_PER_YEAR) {
            throw new BusinessException(ErrorCode.VOLUNTEER_SLOT_FULL, "该年度已达最大志愿表数量(" + MAX_FORMS_PER_YEAR + "份)");
        }

        VolunteerForm form = new VolunteerForm();
        form.setUserId(userId);
        form.setYear(formYear);
        form.setName(name != null && !name.isBlank() ? name : "志愿表" + (count + 1));
        form.setVersion(1);
        form.setItemCount(0);
        form.setMaxItems(DEFAULT_MAX_ITEMS);
        form.setStatus(VolunteerForm.STATUS_ACTIVE);
        form.setCreatedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());

        volunteerFormMapper.insert(form);
        log.info("Volunteer form created: id={}, userId={}, year={}, name={}", form.getId(), userId, year, form.getName());
        return toResponse(form);
    }

    /**
     * 复制志愿表.
     */
    @Transactional
    public VolunteerFormResponse copyForm(Long formId, CopyFormRequest request) {
        String operationKey = "copy:" + formId + ":" + request.getClientOperationId();
        if (!checkIdempotent(operationKey)) {
            VolunteerForm form = getFormWithOwnershipCheck(formId);
            return toResponse(form);
        }

        UserAccount user = authService.checkLogin();
        VolunteerForm source = getFormWithOwnershipCheck(formId);

        // 检查数量上限
        long count = volunteerFormMapper.selectCount(
                new LambdaQueryWrapper<VolunteerForm>()
                        .eq(VolunteerForm::getUserId, user.getId())
                        .eq(VolunteerForm::getYear, source.getYear())
        );
        if (count >= MAX_FORMS_PER_YEAR) {
            throw new BusinessException(ErrorCode.VOLUNTEER_SLOT_FULL, "该年度已达最大志愿表数量(" + MAX_FORMS_PER_YEAR + "份)");
        }

        // 创建新志愿表
        VolunteerForm newForm = new VolunteerForm();
        newForm.setUserId(user.getId());
        newForm.setYear(source.getYear());
        newForm.setName(request.getNewName());
        newForm.setVersion(1);
        newForm.setItemCount(0);
        newForm.setMaxItems(source.getMaxItems() != null ? source.getMaxItems() : DEFAULT_MAX_ITEMS);
        newForm.setStatus(VolunteerForm.STATUS_ACTIVE);
        newForm.setCreatedAt(LocalDateTime.now());
        newForm.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.insert(newForm);

        // 复制志愿项
        List<VolunteerItem> sourceItems = volunteerItemMapper.selectList(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .orderByAsc(VolunteerItem::getSortOrder)
        );

        if (!sourceItems.isEmpty()) {
            for (VolunteerItem item : sourceItems) {
                VolunteerItem newItem = new VolunteerItem();
                newItem.setFormId(newForm.getId());
                newItem.setPlanId(item.getPlanId());
                copySnapshot(item, newItem);
                newItem.setSortOrder(item.getSortOrder());
                newItem.setNote(item.getNote());
                newItem.setAddedAt(LocalDateTime.now());
                newItem.setUpdatedAt(LocalDateTime.now());
                volunteerItemMapper.insert(newItem);
            }
            // 更新 itemCount
            newForm.setItemCount(sourceItems.size());
            volunteerFormMapper.updateById(newForm);
        }

        log.info("Volunteer form copied: sourceId={}, newId={}, name={}", formId, newForm.getId(), newForm.getName());
        recordIdempotent(operationKey);
        return toResponse(newForm);
    }

    /**
     * 删除志愿表（至少保留一份）.
     */
    @Transactional
    public void deleteForm(Long formId) {
        VolunteerForm form = getFormWithOwnershipCheck(formId);

        long count = volunteerFormMapper.selectCount(
                new LambdaQueryWrapper<VolunteerForm>()
                        .eq(VolunteerForm::getUserId, form.getUserId())
                        .eq(VolunteerForm::getYear, form.getYear())
        );
        if (count <= MIN_FORMS) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_LOCKED, "至少保留一份志愿表");
        }

        // 删除所有志愿项
        volunteerItemMapper.delete(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
        );
        // 删除志愿表
        volunteerFormMapper.deleteById(formId);
        log.info("Volunteer form deleted: id={}, name={}", formId, form.getName());
    }

    /**
     * 重命名志愿表.
     */
    @Transactional
    public void renameForm(Long formId, String name) {
        VolunteerForm form = getFormWithOwnershipCheck(formId);
        form.setName(name);
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);
        log.info("Volunteer form renamed: id={}, newName={}", formId, name);
    }

    // ==================== 志愿项管理 ====================

    /**
     * 添加志愿项.
     */
    @Transactional
    public VolunteerFormDetailResponse addItem(Long formId, Long planId, Integer expectedVersion, String clientOperationId) {
        AddVolunteerItemRequest request = new AddVolunteerItemRequest();
        request.setPlanId(planId);
        request.setExpectedVersion(expectedVersion);
        request.setClientOperationId(clientOperationId);
        return addItem(formId, request);
    }

    @Transactional
    public VolunteerFormDetailResponse addItem(Long formId, AddVolunteerItemRequest request) {
        Long planId = request.getPlanId();
        Integer expectedVersion = request.getExpectedVersion();
        String clientOperationId = request.getClientOperationId();
        String operationKey = "addItem:" + formId + ":" + planId + ":" + clientOperationId;
        if (!checkIdempotent(operationKey)) {
            return getFormDetail(formId);
        }

        VolunteerForm form = getFormWithOwnershipCheck(formId);

        // 乐观锁校验
        checkVersion(form, expectedVersion);

        // 检查志愿表已锁定
        if (VolunteerForm.STATUS_ARCHIVED.equals(form.getStatus())) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_LOCKED);
        }

        // 检查上限（maxItems 为 null 时不限容量）
        if (form.getMaxItems() != null && form.getItemCount() >= form.getMaxItems()) {
            throw new BusinessException(ErrorCode.VOLUNTEER_ITEM_LIMIT_REACHED);
        }

        // 文件模型推荐可能尚未入库，因此允许通过请求快照创建志愿项。
        EnrollmentPlan plan = enrollmentPlanService.getPlanById(planId);
        if (plan == null && !hasSnapshot(request)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_PLAN_NOT_FOUND);
        }

        // 检查重复
        Long dupCount = volunteerItemMapper.selectCount(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .eq(VolunteerItem::getPlanId, planId)
        );
        if (dupCount > 0) {
            throw new BusinessException(ErrorCode.VOLUNTEER_PLAN_ALREADY_ADDED);
        }

        // 添加到末尾
        int newSortOrder = form.getItemCount() + 1;
        VolunteerItem item = new VolunteerItem();
        item.setFormId(formId);
        item.setPlanId(planId);
        applySnapshot(item, request);
        if (plan != null) {
            item.setMajorName(defaultIfBlank(item.getMajorName(), plan.getMajorName()));
            item.setMajorCode(defaultIfBlank(item.getMajorCode(), plan.getMajorCode()));
            item.setPlanCount(item.getPlanCount() != null ? item.getPlanCount() : plan.getPlanCount());
            item.setTuition(item.getTuition() != null ? item.getTuition() : plan.getTuition());
            item.setSubjectRequirementText(defaultIfBlank(item.getSubjectRequirementText(), plan.getSubjectRequirementText()));
            item.setPlanStatus(defaultIfBlank(item.getPlanStatus(), plan.getPlanStatus()));
        }
        item.setSortOrder(newSortOrder);
        item.setAddedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        volunteerItemMapper.insert(item);

        // 更新志愿表: itemCount + 1；version 由 MyBatis-Plus @Version 自动递增。
        form.setItemCount(form.getItemCount() + 1);
        form.setUpdatedAt(LocalDateTime.now());
        int updated = volunteerFormMapper.updateById(form);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_VERSION_CONFLICT);
        }

        log.info("Volunteer item added: formId={}, planId={}, sortOrder={}", formId, planId, newSortOrder);
        recordIdempotent(operationKey);
        return getFormDetail(formId);
    }

    private static boolean hasSnapshot(AddVolunteerItemRequest request) {
        return request.getSchoolName() != null && !request.getSchoolName().isBlank()
                && request.getMajorName() != null && !request.getMajorName().isBlank();
    }

    private static void applySnapshot(VolunteerItem item, AddVolunteerItemRequest request) {
        item.setSchoolId(request.getSchoolId());
        item.setSchoolName(request.getSchoolName());
        item.setSchoolCode(request.getSchoolCode());
        item.setMajorName(request.getMajorName());
        item.setMajorCode(request.getMajorCode());
        item.setProvince(request.getProvince());
        item.setCity(request.getCity());
        item.setSchoolType(request.getSchoolType());
        item.setEnrollmentType(request.getEnrollmentType());
        item.setProbability(request.getProbability());
        item.setLabel(request.getLabel());
        item.setPlanCount(request.getPlanCount());
        item.setTuition(request.getTuition());
        item.setSubjectRequirementText(request.getSubjectRequirementText());
        item.setPlanStatus(request.getPlanStatus());
        item.setLastYearMinRank(request.getLastYearMinRank());
        item.setTwoYearMinRank(request.getTwoYearMinRank());
        item.setThreeYearMinRank(request.getThreeYearMinRank());
        item.setPredictedRank(request.getPredictedRank());
    }

    private static void copySnapshot(VolunteerItem source, VolunteerItem target) {
        target.setSchoolId(source.getSchoolId());
        target.setSchoolName(source.getSchoolName());
        target.setSchoolCode(source.getSchoolCode());
        target.setMajorName(source.getMajorName());
        target.setMajorCode(source.getMajorCode());
        target.setProvince(source.getProvince());
        target.setCity(source.getCity());
        target.setSchoolType(source.getSchoolType());
        target.setEnrollmentType(source.getEnrollmentType());
        target.setProbability(source.getProbability());
        target.setLabel(source.getLabel());
        target.setPlanCount(source.getPlanCount());
        target.setTuition(source.getTuition());
        target.setSubjectRequirementText(source.getSubjectRequirementText());
        target.setPlanStatus(source.getPlanStatus());
        target.setLastYearMinRank(source.getLastYearMinRank());
        target.setTwoYearMinRank(source.getTwoYearMinRank());
        target.setThreeYearMinRank(source.getThreeYearMinRank());
        target.setPredictedRank(source.getPredictedRank());
    }

    private static String defaultIfBlank(String current, String fallback) {
        return current == null || current.isBlank() ? fallback : current;
    }

    /**
     * 删除志愿项.
     */
    @Transactional
    public void removeItem(Long formId, Long itemId, Integer expectedVersion, String clientOperationId) {
        String operationKey = "removeItem:" + formId + ":" + itemId + ":" + clientOperationId;
        if (!checkIdempotent(operationKey)) return;

        VolunteerForm form = getFormWithOwnershipCheck(formId);
        checkVersion(form, expectedVersion);

        VolunteerItem item = volunteerItemMapper.selectById(itemId);
        if (item == null || !item.getFormId().equals(formId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "志愿项不存在");
        }

        int removedSort = item.getSortOrder();
        volunteerItemMapper.deleteById(itemId);

        // 重整 sortOrder: 将后续序号前移
        reorderAfterRemove(formId, removedSort);

        // 更新志愿表
        form.setItemCount(form.getItemCount() - 1);
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);

        log.info("Volunteer item removed: formId={}, itemId={}", formId, itemId);
        recordIdempotent(operationKey);
    }

    /**
     * 批量删除志愿项.
     */
    @Transactional
    public void batchRemoveItems(Long formId, List<Long> itemIds, Integer expectedVersion, String clientOperationId) {
        String operationKey = "batchRemove:" + formId + ":" + itemIds.hashCode() + ":" + clientOperationId;
        if (!checkIdempotent(operationKey)) return;

        VolunteerForm form = getFormWithOwnershipCheck(formId);
        checkVersion(form, expectedVersion);

        List<VolunteerItem> items = volunteerItemMapper.selectBatchIds(itemIds);
        for (VolunteerItem item : items) {
            if (!item.getFormId().equals(formId)) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "志愿项" + item.getId() + "不属于该志愿表");
            }
        }

        // 收集要删除的 sortOrder，排序
        List<Integer> removedOrders = items.stream()
                .map(VolunteerItem::getSortOrder)
                .sorted()
                .collect(Collectors.toList());

        // 删除
        volunteerItemMapper.deleteBatchIds(itemIds);

        // 重整排序
        reorderAfterBatchRemove(formId, removedOrders);

        // 更新志愿表
        form.setItemCount(form.getItemCount() - items.size());
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);

        log.info("Batch removed {} items from formId={}", items.size(), formId);
        recordIdempotent(operationKey);
    }

    /**
     * 移动志愿项排序.
     * 使用 CASE WHEN 在事务内完成排序重整，确保唯一序号约束不冲突.
     */
    @Transactional
    public void moveItem(Long formId, Long itemId, Integer targetPosition, Integer expectedVersion, String clientOperationId) {
        String operationKey = "moveItem:" + formId + ":" + itemId + ":" + targetPosition + ":" + clientOperationId;
        if (!checkIdempotent(operationKey)) return;

        VolunteerForm form = getFormWithOwnershipCheck(formId);
        checkVersion(form, expectedVersion);

        VolunteerItem item = volunteerItemMapper.selectById(itemId);
        if (item == null || !item.getFormId().equals(formId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "志愿项不存在");
        }

        int currentPos = item.getSortOrder();
        int targetPos = Math.min(targetPosition, form.getItemCount());

        if (currentPos == targetPos) {
            recordIdempotent(operationKey);
            return;
        }

        // 使用中间值避免唯一约束冲突
        // 第一步: 将当前项移到临时高位（+10000）
        int tempPos = form.getItemCount() + 10000;
        volunteerItemMapper.update(null,
                new LambdaUpdateWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getId, itemId)
                        .set(VolunteerItem::getSortOrder, tempPos)
        );

        if (currentPos < targetPos) {
            // 向下移动: current 后的项 sortOrder 减 1
            volunteerItemMapper.update(null,
                    new LambdaUpdateWrapper<VolunteerItem>()
                            .eq(VolunteerItem::getFormId, formId)
                            .gt(VolunteerItem::getSortOrder, currentPos)
                            .le(VolunteerItem::getSortOrder, targetPos)
                            .setSql("sort_order = sort_order - 1")
            );
        } else {
            // 向上移动: target 及之后的项 sortOrder 加 1
            volunteerItemMapper.update(null,
                    new LambdaUpdateWrapper<VolunteerItem>()
                            .eq(VolunteerItem::getFormId, formId)
                            .ge(VolunteerItem::getSortOrder, targetPos)
                            .lt(VolunteerItem::getSortOrder, currentPos)
                            .setSql("sort_order = sort_order + 1")
            );
        }

        // 第二步: 将临时位置的项放到目标位置
        volunteerItemMapper.update(null,
                new LambdaUpdateWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getId, itemId)
                        .set(VolunteerItem::getSortOrder, targetPos)
        );

        // 更新志愿表版本
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);

        log.info("Volunteer item moved: formId={}, itemId={}, {} -> {}", formId, itemId, currentPos, targetPos);
        recordIdempotent(operationKey);
    }

    /**
     * 更新志愿项备注.
     */
    @Transactional
    public void updateNote(Long formId, Long itemId, String note, Integer expectedVersion, String clientOperationId) {
        String operationKey = "updateNote:" + formId + ":" + itemId + ":" + clientOperationId;
        if (!checkIdempotent(operationKey)) return;

        VolunteerForm form = getFormWithOwnershipCheck(formId);
        checkVersion(form, expectedVersion);

        VolunteerItem item = volunteerItemMapper.selectById(itemId);
        if (item == null || !item.getFormId().equals(formId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "志愿项不存在");
        }

        if (note != null && note.length() > VolunteerItem.MAX_NOTE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "备注最长" + VolunteerItem.MAX_NOTE_LENGTH + "字符");
        }

        item.setNote(note);
        item.setUpdatedAt(LocalDateTime.now());
        volunteerItemMapper.updateById(item);

        // 更新志愿表版本
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);

        log.info("Volunteer item note updated: formId={}, itemId={}", formId, itemId);
        recordIdempotent(operationKey);
    }

    /**
     * 修改志愿表容量（null = 不限）.
     */
    @Transactional
    public VolunteerFormResponse updateMaxItems(Long formId, Integer maxItems) {
        VolunteerForm form = getFormWithOwnershipCheck(formId);
        if (maxItems != null && maxItems < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "容量至少为 1");
        }
        form.setMaxItems(maxItems);
        form.setUpdatedAt(LocalDateTime.now());
        volunteerFormMapper.updateById(form);
        log.info("Volunteer form maxItems updated: formId={}, maxItems={}", formId, maxItems);
        return toResponse(form);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取志愿表并校验归属.
     */
    private VolunteerForm getFormWithOwnershipCheck(Long formId) {
        VolunteerForm form = volunteerFormMapper.selectById(formId);
        if (form == null) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_NOT_FOUND);
        }
        Long currentUserId = authService.checkLogin().getId();
        if (!currentUserId.equals(form.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return form;
    }

    /**
     * 乐观锁版本校验.
     */
    private void checkVersion(VolunteerForm form, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(form.getVersion())) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_VERSION_CONFLICT);
        }
    }

    /**
     * 删除后重整 sortOrder: removedSort 之后的项前移.
     */
    private void reorderAfterRemove(Long formId, int removedSort) {
        volunteerItemMapper.update(null,
                new LambdaUpdateWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .gt(VolunteerItem::getSortOrder, removedSort)
                        .setSql("sort_order = sort_order - 1")
        );
    }

    /**
     * 批量删除后重整 sortOrder.
     * removedOrders 已排序，从最后一个开始处理.
     */
    private void reorderAfterBatchRemove(Long formId, List<Integer> removedOrders) {
        for (int i = removedOrders.size() - 1; i >= 0; i--) {
            int removedSort = removedOrders.get(i);
            volunteerItemMapper.update(null,
                    new LambdaUpdateWrapper<VolunteerItem>()
                            .eq(VolunteerItem::getFormId, formId)
                            .gt(VolunteerItem::getSortOrder, removedSort)
                            .setSql("sort_order = sort_order - 1")
            );
        }
    }

    /**
     * 幂等检查: 已存在且未过期则返回 false（重复请求）.
     */
    private boolean checkIdempotent(String key) {
        if (key == null || key.isBlank()) return true;
        Long ts = idempotentCache.get(key);
        if (ts != null && System.currentTimeMillis() - ts < IDEMPOTENT_TTL_MS) {
            log.debug("Idempotent duplicate request: key={}", key);
            return false;
        }
        // 定期清理过期条目
        if (idempotentCache.size() > 1000) {
            idempotentCache.entrySet().removeIf(e ->
                    System.currentTimeMillis() - e.getValue() > IDEMPOTENT_TTL_MS);
        }
        return true;
    }

    private void recordIdempotent(String key) {
        if (key != null && !key.isBlank()) {
            idempotentCache.put(key, System.currentTimeMillis());
        }
    }

    private VolunteerFormResponse toResponse(VolunteerForm form) {
        return VolunteerFormResponse.builder()
                .id(form.getId())
                .userId(form.getUserId())
                .year(form.getYear())
                .name(form.getName())
                .version(form.getVersion())
                .itemCount(form.getItemCount())
                .maxItems(form.getMaxItems())
                .status(form.getStatus())
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .build();
    }

    private VolunteerFormDetailResponse.VolunteerItemResponse toItemResponse(VolunteerItem item) {
        String schoolName = item.getSchoolName();
        String majorName = item.getMajorName();
        String subjectRequirementText = item.getSubjectRequirementText();
        String planStatus = item.getPlanStatus();
        try {
            EnrollmentPlan plan = enrollmentPlanService.getPlanById(item.getPlanId());
            if (plan != null) {
                schoolName = defaultIfBlank(schoolName, plan.getSchoolName());
                majorName = defaultIfBlank(majorName, plan.getMajorName());
                subjectRequirementText = defaultIfBlank(subjectRequirementText, plan.getSubjectRequirementText());
                planStatus = defaultIfBlank(planStatus, plan.getPlanStatus());
            }
        } catch (Exception e) {
            log.debug("Failed to load plan info for planId={}: {}", item.getPlanId(), e.getMessage());
        }
        return VolunteerFormDetailResponse.VolunteerItemResponse.builder()
                .id(item.getId())
                .planId(item.getPlanId())
                .schoolId(item.getSchoolId())
                .schoolCode(item.getSchoolCode())
                .schoolName(schoolName)
                .majorName(majorName)
                .majorCode(item.getMajorCode())
                .province(item.getProvince())
                .city(item.getCity())
                .schoolType(item.getSchoolType())
                .enrollmentType(item.getEnrollmentType())
                .probability(item.getProbability())
                .label(item.getLabel())
                .planCount(item.getPlanCount())
                .tuition(item.getTuition())
                .subjectRequirementText(subjectRequirementText)
                .planStatus(planStatus)
                .lastYearMinRank(item.getLastYearMinRank())
                .twoYearMinRank(item.getTwoYearMinRank())
                .threeYearMinRank(item.getThreeYearMinRank())
                .predictedRank(item.getPredictedRank())
                .sortOrder(item.getSortOrder())
                .note(item.getNote())
                .addedAt(item.getAddedAt())
                .build();
    }
}
