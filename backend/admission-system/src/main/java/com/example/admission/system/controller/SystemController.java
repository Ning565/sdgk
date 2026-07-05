package com.example.admission.system.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.system.dto.DictionaryResponse;
import com.example.admission.system.dto.YearConfigResponse;
import com.example.admission.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置控制器.
 *
 * <p>基础路径： {@code /api/v1/configs}</p>
 * <p>公开接口，无需认证。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    /**
     * 获取活跃年度列表.
     *
     * <p>返回所有开放的年度的配置信息，包括分数范围、志愿限额等。</p>
     *
     * @return 活跃年度配置列表
     */
    @GetMapping("/years")
    public ApiResponse<List<YearConfigResponse>> listActiveYears() {
        log.debug("Listing active years");
        List<YearConfigResponse> years = systemService.listActiveYears();
        return ApiResponse.success(years);
    }

    /**
     * 获取字典数据.
     *
     * <p>支持按类型筛选，不传 type 则返回全部。</p>
     *
     * <h3>支持的字典类型</h3>
     * <ul>
     *   <li>education_level — 教育层次</li>
     *   <li>school_type — 院校性质</li>
     *   <li>school_tag — 院校标签</li>
     *   <li>enrollment_type — 招生类型</li>
     *   <li>plan_status — 计划状态</li>
     *   <li>subject — 选考科目</li>
     *   <li>volunteer_label — 志愿标签</li>
     *   <li>confidence — 置信度</li>
     * </ul>
     *
     * @param type 字典类型（可选）
     * @return 字典项列表
     */
    @GetMapping("/dictionaries")
    public ApiResponse<List<DictionaryResponse>> getDictionaries(
            @RequestParam(value = "type", required = false) String type) {
        log.debug("Getting dictionaries: type={}", type);
        List<DictionaryResponse> dicts = systemService.getDictionaries(type);
        return ApiResponse.success(dicts);
    }
}
