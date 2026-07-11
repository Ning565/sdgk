package com.example.admission.recommendation.mapper;

import com.example.admission.recommendation.dto.RecommendationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 推荐查询 Mapper.
 *
 * <p>执行复杂的三层过滤 SQL（硬过滤 + 用户筛选 + 排序），
 * 联查 enrollment_plan、school、standard_major、admission_history、
 * prediction_result 五张表。</p>
 */
@Mapper
public interface RecommendationMapper {

    /**
     * 执行推荐搜索查询，返回符合条件的专业计划列表.
     *
     * <p>包含三层过滤：硬过滤（版本、年度、状态、选科、教育层次）、
     * 用户筛选（关键词、地区、院校性质、专业门类、学费等）、排序。</p>
     *
     * @param req 请求对象              推荐搜索请求
     * @param dataVersionId    当前生效的招生计划数据版本ID
     * @param historyVersionId 当前生效的历史录取数据版本ID（预留，当前通过 year-1 关联）
     * @param userId           用户ID（用于 JOIN prediction_result，可选）
     * @param subjectComboIndex 考生选科组合索引
     * @param candidateRank    考生位次（用于 rankDiff 排序计算）
     * @param offset           分页偏移量
     * @param limit            分页大小
     * @return 符合条件的专业计划 VO 列表
     */
    List<RecommendationPlanVO> searchPlans(@Param("req") RecommendationRequest req,
                                           @Param("dataVersionId") Long dataVersionId,
                                           @Param("historyVersionId") Long historyVersionId,
                                           @Param("userId") Long userId,
                                           @Param("subjectComboIndex") Integer subjectComboIndex,
                                           @Param("candidateRank") Integer candidateRank,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /**
     * 统计符合条件的专业计划总数.
     *
     * @param req 请求对象              推荐搜索请求
     * @param dataVersionId    当前生效的招生计划数据版本ID
     * @param historyVersionId 当前生效的历史录取数据版本ID
     * @param userId           用户ID
     * @param subjectComboIndex 考生选科组合索引
     * @return 符合条件的记录总数
     */
    long countPlans(@Param("req") RecommendationRequest req,
                    @Param("dataVersionId") Long dataVersionId,
                    @Param("historyVersionId") Long historyVersionId,
                    @Param("userId") Long userId,
                    @Param("subjectComboIndex") Integer subjectComboIndex);

    long countSchools(@Param("req") RecommendationRequest req,
                      @Param("dataVersionId") Long dataVersionId,
                      @Param("historyVersionId") Long historyVersionId,
                      @Param("userId") Long userId,
                      @Param("subjectComboIndex") Integer subjectComboIndex);

    /**
     * 拉取符合硬过滤与用户筛选的全量候选计划（不做概率排序与截断）.
     *
     * <p>用于本科数据库推荐路径：先取全量候选池，概率计算、位次兜底与
     * 冲稳保混合全部放到 Java 侧完成，避免 SQL 层用概率排序 + LIMIT 造成
     * 无 predicted_rank 计划（NULL 排在最前）霸占结果。</p>
     *
     * @param req              推荐搜索请求
     * @param dataVersionId    当前生效的招生计划数据版本ID
     * @param subjectComboIndex 考生选科组合索引
     * @param limit            候选池上限
     * @return 候选计划 VO 列表
     */
    List<RecommendationPlanVO> searchCandidates(@Param("req") RecommendationRequest req,
                                                @Param("dataVersionId") Long dataVersionId,
                                                @Param("subjectComboIndex") Integer subjectComboIndex,
                                                @Param("limit") int limit);
}
