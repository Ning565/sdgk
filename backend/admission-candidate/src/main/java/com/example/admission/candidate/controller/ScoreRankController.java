package com.example.admission.candidate.controller;

import com.example.admission.candidate.dto.ScoreRankResolveResponse;
import com.example.admission.candidate.service.ScoreRankService;
import com.example.admission.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 分数位次匹配 REST 控制器.
 *
 * <p>基础路径： {@code /api/v1/score-ranks}</p>
 * <p>无需登录即可查询（公开的一分一段表数据）。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>GET /resolve?year=&score= — 根据年份和分数查询累计人数（省排名位次）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/score-ranks")
@RequiredArgsConstructor
public class ScoreRankController {

    private final ScoreRankService scoreRankService;

    /**
     * 根据年份和分数查询省排名位次.
     *
     * <p>查询当年生效的一分一段表数据版本，精确匹配分数对应的累计人数。
     * 累计人数即为该分数对应的省排名位次。</p>
     *
     * <p><b>注意：</b>
     * <ul>
     *   <li>精确匹配，不会取临近分数</li>
     *   <li>如果当年一分一段表未发布，返回 SCORE_NOT_FOUND 错误</li>
     *   <li>查询结果缓存建议由客户端实现</li>
     * </ul>
     * </p>
     *
     * @param year 年份  高考年份（查询参数）
     * @param score 高考分数（查询参数）
     * @return ApiResponse 包含 ScoreRankResolveResponse
     */
    @GetMapping("/resolve")
    public ApiResponse<ScoreRankResolveResponse> resolve(@RequestParam("year") Integer year,
                                                          @RequestParam("score") Integer score) {
        log.info("Score rank resolve request: year={}, score={}", year, score);

        ScoreRankResolveResponse response = scoreRankService.resolve(year, score);

        log.info("Score rank resolved: year={}, score={}, cumulativeCount={}",
                year, score, response.getCumulativeCount());

        return ApiResponse.success(response);
    }
}
