package com.example.admission.dataimport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.entity.DataVersion;
import com.example.admission.dataimport.entity.ImportBatch;
import com.example.admission.dataimport.mapper.ActiveDataVersionMapper;
import com.example.admission.dataimport.mapper.DataVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 原子发布服务.
 *
 * <p>提供数据的原子发布和版本回滚能力。
 * 发布流程基于数据库行锁（SELECT ... FOR UPDATE）保证
 * active_data_version 切换的原子性和一致性。</p>
 *
 * <h3>发布流程</h3>
 * <ol>
 *   <li>校验批次状态为 READY</li>
 *   <li>锁定 active_data_version 行 (SELECT ... FOR UPDATE)</li>
 *   <li>创建新版本并设状态为 PUBLISHED</li>
 *   <li>旧版本设 ARCHIVED</li>
 *   <li>更新 active_data_version 指针指向新版本</li>
 *   <li>更新批次状态为 PUBLISHED</li>
 *   <li>提交事务</li>
 * </ol>
 *
 * @author admission-system
 */
@Service
public class DataPublishService {

    private static final Logger log = LoggerFactory.getLogger(DataPublishService.class);

    private final ImportBatchService importBatchService;
    private final DataVersionMapper dataVersionMapper;
    private final ActiveDataVersionMapper activeDataVersionMapper;

    public DataPublishService(ImportBatchService importBatchService,
                               DataVersionMapper dataVersionMapper,
                               ActiveDataVersionMapper activeDataVersionMapper) {
        this.importBatchService = importBatchService;
        this.dataVersionMapper = dataVersionMapper;
        this.activeDataVersionMapper = activeDataVersionMapper;
    }

    /**
     * 原子发布数据版本.
     *
     * <p>将指定批次的数据发布为生效版本。整个操作在事务中执行，
     * 通过行锁保证并发安全。</p>
     *
     * @param batchId     导入批次ID
     * @param publishedBy 发布人ID（admin_user.id）
     * @return 新创建的已发布数据版本
     * @throws BusinessException 业务异常 如果批次不可发布或发布失败
     */
    @Transactional
    public DataVersion publish(Long batchId, Long publishedBy) {
        // 1. 校验批次状态
        ImportBatch batch = importBatchService.getBatch(batchId);
        if (!ImportBatch.STATUS_READY.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.IMPORT_PUBLISH_FAILED,
                    "批次状态不允许发布，当前状态: " + batch.getStatus() + "，需要状态: READY");
        }

        String dataType = batch.getDataType();
        Integer year = batch.getYear();

        log.info("Starting atomic publish: batchId={}, dataType={}, year={}", batchId, dataType, year);

        // 2. 锁定 active_data_version 行（SELECT ... FOR UPDATE）
        ActiveDataVersion currentActive = activeDataVersionMapper.selectForUpdate(dataType, year);

        // 3. 计算新版本号
        int maxVersionNo = dataVersionMapper.selectMaxVersionNo(dataType, year);
        int newVersionNo = maxVersionNo + 1;

        // 4. 计算校验和
        String checksum = computeChecksum(batchId, dataType, year);

        // 5. 创建新版本并发布
        DataVersion newVersion = new DataVersion();
        newVersion.setDataType(dataType);
        newVersion.setYear(year);
        newVersion.setVersionNo(newVersionNo);
        newVersion.setStatus(DataVersion.STATUS_PUBLISHED);
        newVersion.setSourceBatchId(batchId);
        newVersion.setRowCount(batch.getTotalRows());
        newVersion.setChecksum(checksum);
        newVersion.setPublishedBy(publishedBy);
        newVersion.setPublishedAt(LocalDateTime.now());
        newVersion.setCreatedAt(LocalDateTime.now());

        dataVersionMapper.insert(newVersion);
        log.info("Created new data version: id={}, versionNo={}", newVersion.getId(), newVersionNo);

        // 6. 归档旧版本
        if (currentActive != null) {
            DataVersion oldVersion = dataVersionMapper.selectById(currentActive.getDataVersionId());
            if (oldVersion != null && DataVersion.STATUS_PUBLISHED.equals(oldVersion.getStatus())) {
                oldVersion.setStatus(DataVersion.STATUS_ARCHIVED);
                dataVersionMapper.updateById(oldVersion);
                log.info("Archived old version: id={}, versionNo={}", oldVersion.getId(), oldVersion.getVersionNo());
            }
        }

        // 7. 更新 active_data_version 指针
        if (currentActive != null) {
            // 更新已有记录
            currentActive.setDataVersionId(newVersion.getId());
            currentActive.setUpdatedAt(LocalDateTime.now());
            activeDataVersionMapper.updateById(currentActive);
        } else {
            // 首次发布，创建新记录
            ActiveDataVersion newActive = new ActiveDataVersion();
            newActive.setDataType(dataType);
            newActive.setYear(year);
            newActive.setDataVersionId(newVersion.getId());
            newActive.setUpdatedAt(LocalDateTime.now());
            activeDataVersionMapper.insert(newActive);
        }
        log.info("Updated active data version: dataType={}, year={}, versionId={}",
                dataType, year, newVersion.getId());

        // 8. 更新批次状态
        importBatchService.updateStatus(batchId, ImportBatch.STATUS_PUBLISHED);

        log.info("Publish completed: batchId={}, versionId={}, versionNo={}, publishedBy={}",
                batchId, newVersion.getId(), newVersionNo, publishedBy);

        return newVersion;
    }

    /**
     * 回滚到指定版本.
     *
     * <p>回滚操作本质上是创建一个新版本，其数据复制自目标旧版本的数据。
     * 这样保持了版本历史的完整性和可追溯性。</p>
     *
     * @param versionId   目标回滚版本ID
     * @param adminUserId 操作人ID
     * @return 新创建的回滚数据版本
     * @throws BusinessException 业务异常 如果版本不存在或回滚失败
     */
    @Transactional
    public DataVersion rollback(Long versionId, Long adminUserId) {
        DataVersion targetVersion = Optional.ofNullable(dataVersionMapper.selectById(versionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "目标版本不存在: " + versionId));

        String dataType = targetVersion.getDataType();
        Integer year = targetVersion.getYear();

        log.info("Starting rollback: versionId={}, dataType={}, year={}, adminUserId={}",
                versionId, dataType, year, adminUserId);

        // 锁定 active_data_version
        ActiveDataVersion currentActive = activeDataVersionMapper.selectForUpdate(dataType, year);

        // 计算新版本号
        int maxVersionNo = dataVersionMapper.selectMaxVersionNo(dataType, year);
        int newVersionNo = maxVersionNo + 1;

        // 创建回滚版本（标记为 PUBLISHED）
        DataVersion rollbackVersion = new DataVersion();
        rollbackVersion.setDataType(dataType);
        rollbackVersion.setYear(year);
        rollbackVersion.setVersionNo(newVersionNo);
        rollbackVersion.setStatus(DataVersion.STATUS_PUBLISHED);
        rollbackVersion.setSourceBatchId(targetVersion.getSourceBatchId());
        rollbackVersion.setRowCount(targetVersion.getRowCount());
        rollbackVersion.setChecksum(targetVersion.getChecksum());
        rollbackVersion.setPublishedBy(adminUserId);
        rollbackVersion.setPublishedAt(LocalDateTime.now());
        rollbackVersion.setCreatedAt(LocalDateTime.now());

        dataVersionMapper.insert(rollbackVersion);
        log.info("Created rollback version: id={}, versionNo={}", rollbackVersion.getId(), newVersionNo);

        // 归档当前生效版本
        if (currentActive != null) {
            DataVersion currentVersion = dataVersionMapper.selectById(currentActive.getDataVersionId());
            if (currentVersion != null && DataVersion.STATUS_PUBLISHED.equals(currentVersion.getStatus())) {
                currentVersion.setStatus(DataVersion.STATUS_ARCHIVED);
                dataVersionMapper.updateById(currentVersion);
                log.info("Archived current version during rollback: id={}, versionNo={}",
                        currentVersion.getId(), currentVersion.getVersionNo());
            }
        }

        // 更新 active_data_version 指针
        if (currentActive != null) {
            currentActive.setDataVersionId(rollbackVersion.getId());
            currentActive.setUpdatedAt(LocalDateTime.now());
            activeDataVersionMapper.updateById(currentActive);
        } else {
            ActiveDataVersion newActive = new ActiveDataVersion();
            newActive.setDataType(dataType);
            newActive.setYear(year);
            newActive.setDataVersionId(rollbackVersion.getId());
            newActive.setUpdatedAt(LocalDateTime.now());
            activeDataVersionMapper.insert(newActive);
        }

        log.info("Rollback completed: versionId={}, newVersionId={}, newVersionNo={}",
                versionId, rollbackVersion.getId(), newVersionNo);

        return rollbackVersion;
    }

    /**
     * 查询数据版本列表.
     *
     * @param dataType 数据类型（可选）
     * @param year 年份     年份（可选）
     * @return 版本列表
     */
    public java.util.List<DataVersion> listVersions(String dataType, Integer year) {
        LambdaQueryWrapper<DataVersion> wrapper = new LambdaQueryWrapper<>();
        if (dataType != null && !dataType.isBlank()) {
            wrapper.eq(DataVersion::getDataType, dataType);
        }
        if (year != null) {
            wrapper.eq(DataVersion::getYear, year);
        }
        wrapper.orderByDesc(DataVersion::getYear)
                .orderByDesc(DataVersion::getVersionNo);
        return dataVersionMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询版本.
     *
     * @param versionId 版本ID
     * @return 版本实体
     */
    public DataVersion getVersion(Long versionId) {
        return Optional.ofNullable(dataVersionMapper.selectById(versionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "数据版本不存在: " + versionId));
    }

    // ==================== 私有方法 ====================

    /**
     * 计算数据校验和（SHA-256）.
     *
     * <p>用于验证版本数据完整性。MVP阶段基于批次信息计算。</p>
     */
    private String computeChecksum(Long batchId, String dataType, Integer year) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = batchId + "|" + dataType + "|" + year + "|" + System.currentTimeMillis();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return "";
        }
    }
}
