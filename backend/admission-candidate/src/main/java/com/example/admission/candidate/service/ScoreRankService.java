package com.example.admission.candidate.service;

import com.example.admission.candidate.dto.ScoreRankResolveResponse;
import com.example.admission.candidate.entity.ScoreRankSegment;
import com.example.admission.candidate.mapper.ScoreRankSegmentMapper;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.entity.DataVersion;
import com.example.admission.dataimport.mapper.ActiveDataVersionMapper;
import com.example.admission.dataimport.mapper.DataVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分数位次匹配服务.
 *
 * <p>根据年份和分数，查询当年生效的一分一段表数据版本，
 * 精确匹配分数对应的累计人数（省排名位次）。
 * 不取临近分数，未匹配时抛出 SCORE_NOT_FOUND 异常。</p>
 *
 * <p><b>数据版本管理：</b>
 * 通过 {@code active_data_version} 表获取每年 {code SCORE_RANK} 类型的生效版本ID，
 * 确保查询的是已发布的最新正式数据。</p>
 *
 * <p><b>位次差计算：</b>
 * 位次差 = 预测位次 - 考生位次，正值表示考生领先于预测。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRankService {

    private final ScoreRankSegmentMapper scoreRankSegmentMapper;
    private final ActiveDataVersionMapper activeDataVersionMapper;
    private final DataVersionMapper dataVersionMapper;

    /** 一分一段表数据类型标识 */
    private static final String DATA_TYPE_SCORE_RANK = "SCORE_RANK";

    /**
     * 根据年份和分数解析省排名位次.
     *
     * <h3>处理流程：</h3>
     * <ol>
     *   <li>查询 active_data_version 获取当年生效的一分一段表版本ID</li>
     *   <li>如果当年一分一段表未发布（无生效版本），提示手动输入</li>
     *   <li>在当期版本中精确匹配分数</li>
     *   <li>命中则返回 cumulativeCount（累计人数 = 省排名位次）</li>
     *   <li>未命中则抛出 SCORE_NOT_FOUND（不取临近分数）</li>
     * </ol>
     *
     * @param year 年份  高考年份
     * @param score 高考分数
     * @return ScoreRankResolveResponse 包含累计人数和数据版本信息
     * @throws BusinessException 业务异常 SCORE_NOT_FOUND 如果分数/版本不存在
     */
    @Transactional(readOnly = true)
    public ScoreRankResolveResponse resolve(Integer year, Integer score) {
        log.info("Resolving score rank: year={}, score={}", year, score);

        // 1. 参数校验
        if (year == null || year <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "年份无效");
        }
        if (score == null || score < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分数无效");
        }

        // 2. 查询当前生效的一分一段表版本
        ActiveDataVersion activeVersion = activeDataVersionMapper.selectOne(
                new LambdaQueryWrapper<ActiveDataVersion>()
                        .eq(ActiveDataVersion::getDataType, DATA_TYPE_SCORE_RANK)
                        .eq(ActiveDataVersion::getYear, year)
        );

        if (activeVersion == null || activeVersion.getDataVersionId() == null) {
            log.warn("No active score-rank version for year={}, please publish data first or input rank manually", year);
            throw new BusinessException(ErrorCode.SCORE_NOT_FOUND,
                    "当年一分一段表数据尚未发布，请手动输入位次或等待数据更新");
        }

        Long dataVersionId = activeVersion.getDataVersionId();
        log.debug("Active score-rank version: dataVersionId={}, year={}", dataVersionId, year);

        // 3. 精确匹配分数
        ScoreRankSegment segment = scoreRankSegmentMapper.selectByYearAndScore(year, score, dataVersionId);

        if (segment == null) {
            log.warn("Score not found in rank table: year={}, score={}, dataVersionId={}", year, score, dataVersionId);
            throw new BusinessException(ErrorCode.SCORE_NOT_FOUND,
                    String.format("一分一段表中未找到 %d 年 %d 分的排名数据", year, score));
        }

        log.info("Score rank resolved: year={}, score={}, cumulativeCount={}, dataVersionId={}",
                year, score, segment.getCumulativeCount(), dataVersionId);

        // 4. 获取数据版本名称
        String dataVersionName = null;
        DataVersion dataVersion = dataVersionMapper.selectById(dataVersionId);
        if (dataVersion != null) {
            dataVersionName = String.format("%d年夏季高考一分一段表 v%d", year, dataVersion.getVersionNo());
        }

        // 5. 构建响应
        return ScoreRankResolveResponse.builder()
                .year(year)
                .score(score)
                .cumulativeCount(segment.getCumulativeCount())
                .dataVersionId(dataVersionId)
                .dataVersionName(dataVersionName)
                .updatedAt(activeVersion.getUpdatedAt())
                .build();
    }

    /**
     * 计算位次差.
     *
     * <p>位次差 = 预测位次 - 考生位次。正值表示考生领先于预测录取位次，
     * 即考生的排名更靠前（数值更小），录取概率更大。</p>
     *
     * @param predictedRank 预测录取位次（院校专业的历史录取位次或预测位次）
     * @param candidateRank 考生位次
     * @return 位次差，null 如果任一参数为 null
     */
    public static Integer computeRankDifference(Integer predictedRank, Integer candidateRank) {
        if (predictedRank == null || candidateRank == null) {
            return null;
        }
        return predictedRank - candidateRank;
    }

    /**
     * 检查指定年份的一分一段表是否已发布.
     *
     * @param year 年份 高考年份
     * @return true 如果当年已发布一分一段表数据
     */
    public boolean isScoreRankPublished(Integer year) {
        ActiveDataVersion activeVersion = activeDataVersionMapper.selectOne(
                new LambdaQueryWrapper<ActiveDataVersion>()
                        .eq(ActiveDataVersion::getDataType, DATA_TYPE_SCORE_RANK)
                        .eq(ActiveDataVersion::getYear, year)
        );
        return activeVersion != null && activeVersion.getDataVersionId() != null;
    }
}
