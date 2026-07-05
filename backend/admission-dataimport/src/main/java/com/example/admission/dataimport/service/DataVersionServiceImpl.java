package com.example.admission.dataimport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.mapper.ActiveDataVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据版本查询服务实现.
 *
 * <p>封装 ActiveDataVersionMapper，提供当前生效数据版本的查询，
 * 供 admission-recommendation 等模块通过 Service 接口调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataVersionServiceImpl implements DataVersionService {

    private final ActiveDataVersionMapper activeDataVersionMapper;

    @Override
    @Transactional(readOnly = true)
    public ActiveDataVersion getActiveVersion(String dataType, Integer year) {
        LambdaQueryWrapper<ActiveDataVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActiveDataVersion::getDataType, dataType);
        if (year != null) {
            wrapper.eq(ActiveDataVersion::getYear, year);
        }
        wrapper.last("LIMIT 1");
        return activeDataVersionMapper.selectOne(wrapper);
    }
}
