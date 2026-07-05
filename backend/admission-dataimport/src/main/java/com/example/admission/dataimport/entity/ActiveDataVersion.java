package com.example.admission.dataimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 当前生效数据版本实体，对应 active_data_version 表.
 *
 * <p>记录每种数据类型每个年份当前激活的版本ID，
 * 业务侧查询时只需查此表即可获取当前生效数据。
 * 发布时通过行锁（SELECT ... FOR UPDATE）保证原子性切换。</p>
 *
 * @author admission-system
 */
@TableName("active_data_version")
public class ActiveDataVersion {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据类型: SCORE_RANK / PLAN / HISTORY / LINK */
    @TableField("data_type")
    private String dataType;

    /** 数据年份 */
    @TableField("year")
    private Integer year;

    /** 生效的数据版本ID */
    @TableField("data_version_id")
    private Long dataVersionId;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Long getDataVersionId() {
        return dataVersionId;
    }

    public void setDataVersionId(Long dataVersionId) {
        this.dataVersionId = dataVersionId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
