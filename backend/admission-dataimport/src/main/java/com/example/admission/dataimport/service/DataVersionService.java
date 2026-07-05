package com.example.admission.dataimport.service;

import com.example.admission.dataimport.entity.ActiveDataVersion;

/**
 * 数据版本查询服务接口.
 *
 * <p>由 admission-dataimport 模块实现，
 * 提供当前生效数据版本的查询能力。</p>
 */
public interface DataVersionService {

    /**
     * 查询指定数据类型和年度的当前生效版本.
     *
     * @param dataType 数据类型: PLAN / HISTORY / SCORE_RANK / LINK
     * @param year 年份     数据年度
     * @return 当前生效版本实体，不存在时返回 null
     */
    ActiveDataVersion getActiveVersion(String dataType, Integer year);
}
