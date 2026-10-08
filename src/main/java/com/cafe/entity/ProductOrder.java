package com.cafe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品消费记账 */
@Data
@TableName("product_order")
public class ProductOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的上机记录ID，可为空 */
    private Long sessionId;

    private Long memberId;

    private Long productId;

    private Integer quantity;

    /** 消费金额 = 单价 × 数量 */
    private BigDecimal amount;

    /** 经办收银员ID */
    private Long operatorId;

    private LocalDateTime createTime;

    /* ---------- 联表回显 ---------- */

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private String memberName;
}
