package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 门店公告 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 公告正文 */
    private String content;

    /** 发布人（管理员ID） */
    private Long publisherId;

    /** 1 已发布 / 0 已撤下 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String publisherName;
}
