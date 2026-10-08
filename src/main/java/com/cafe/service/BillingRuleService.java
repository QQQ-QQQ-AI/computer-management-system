package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.BillingRule;
import com.cafe.mapper.BillingRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计费规则服务。
 *
 * 设计目标就是「调价只改数据不改代码」：区域单价、包时套餐、会员折扣
 * 全部存放于 billing_rule 表，管理员在后台调整后立即生效，无需修改程序重新部署。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRuleService {

    private final BillingRuleMapper billingRuleMapper;

    public List<BillingRule> listAll() {
        return billingRuleMapper.selectList(new LambdaQueryWrapper<BillingRule>()
                .orderByAsc(BillingRule::getId));
    }

    /** 启用中的规则，结算时按区域匹配 */
    public List<BillingRule> listEnabled() {
        return billingRuleMapper.selectList(new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getStatus, Constants.ENABLED)
                .orderByAsc(BillingRule::getId));
    }

    public BillingRule getById(Long id) {
        BillingRule rule = billingRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("计费规则不存在，ID=" + id);
        }
        return rule;
    }

    public BillingRule getByArea(String area) {
        BillingRule rule = billingRuleMapper.selectOne(new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getArea, area).last("LIMIT 1"));
        if (rule == null) {
            throw new BizException("区域「" + area + "」尚未配置计费规则");
        }
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public BillingRule create(String area, BigDecimal hourPrice, Integer packageHours,
                              BigDecimal packagePrice, BigDecimal memberDiscount) {
        validate(area, hourPrice, packageHours, packagePrice, memberDiscount);
        Long exists = billingRuleMapper.selectCount(
                new LambdaQueryWrapper<BillingRule>().eq(BillingRule::getArea, area));
        if (exists != null && exists > 0) {
            throw new BizException("区域「" + area + "」已存在计费规则");
        }
        BillingRule rule = new BillingRule();
        if ((packageHours == null) != (packagePrice == null)) throw new BizException("套餐时长与价格必须同时填写");
        if (hourPrice == null) throw new BizException("小时单价不能为空");
        rule.setArea(area.trim());
        rule.setHourPrice(hourPrice);
        rule.setPackageHours(packageHours);
        rule.setPackagePrice(packagePrice);
        rule.setMemberDiscount(memberDiscount == null ? BigDecimal.ONE : memberDiscount);
        rule.setStatus(Constants.ENABLED);
        billingRuleMapper.insert(rule);
        log.info("新增计费规则 区域={} 单价={} 套餐={}小时/{}元 会员折扣={}",
                area, hourPrice, packageHours, packagePrice, memberDiscount);
        return rule;
    }

    /**
     * 修改计费规则。改完立即对后续结算生效，已在进行的上机记录按结算时的规则计算。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BigDecimal hourPrice, Integer packageHours,
                       BigDecimal packagePrice, BigDecimal memberDiscount, Integer status) {
        BillingRule rule = billingRuleMapper.selectForUpdate(id);
        if (rule == null) throw new BizException("计费规则不存在");
        validate(rule.getArea(), hourPrice, packageHours, packagePrice, memberDiscount);

        if (hourPrice != null) {
            rule.setHourPrice(hourPrice);
        }
        // 套餐时长与套餐价必须成对配置，只填一个会导致包时方案无法计算
        if (packageHours != null || packagePrice != null) {
            if (packageHours == null || packagePrice == null) {
                throw new BizException("包时套餐的小时数与价格必须同时填写，或同时留空");
            }
            rule.setPackageHours(packageHours);
            rule.setPackagePrice(packagePrice);
        }
        if (packageHours == null && packagePrice == null) { rule.setPackageHours(null); rule.setPackagePrice(null); }
        if (memberDiscount != null) {
            rule.setMemberDiscount(memberDiscount);
        }
        if (status != null) {
            rule.setStatus(status);
        }

        billingRuleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BillingRule>()
                .eq(BillingRule::getId, rule.getId()).set(BillingRule::getHourPrice, rule.getHourPrice())
                .set(BillingRule::getPackageHours, rule.getPackageHours()).set(BillingRule::getPackagePrice, rule.getPackagePrice())
                .set(BillingRule::getMemberDiscount, rule.getMemberDiscount()).set(BillingRule::getStatus, rule.getStatus()));
        log.info("计费规则已更新 区域={} 单价={} 会员折扣={} 状态={}",
                rule.getArea(), rule.getHourPrice(), rule.getMemberDiscount(), rule.getStatus());
    }

    /** 启用 / 停用规则 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, int status) {
        if (status != Constants.ENABLED && status != Constants.DISABLED) throw new BizException("计费规则状态无效");
        BillingRule rule = getById(id);
        billingRuleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BillingRule>()
                .eq(BillingRule::getId, id).set(BillingRule::getStatus, status));
        log.info("计费规则状态变更 区域={} -> {}", rule.getArea(), status);
    }

    private void validate(String area, BigDecimal hourPrice, Integer packageHours,
                          BigDecimal packagePrice, BigDecimal memberDiscount) {
        for (BigDecimal value : new BigDecimal[]{hourPrice, packagePrice, memberDiscount}) {
            if (value != null && value.scale() > 2) throw new BizException("价格与折扣最多保留两位小数");
        }
        if (area == null || area.isBlank()) {
            throw new BizException("区域名称不能为空");
        }
        if (hourPrice != null && hourPrice.signum() <= 0) {
            throw new BizException("小时单价必须大于 0");
        }
        if (packageHours != null && packageHours <= 0) {
            throw new BizException("包时套餐时长必须大于 0");
        }
        if (packagePrice != null && packagePrice.signum() <= 0) {
            throw new BizException("包时套餐价格必须大于 0");
        }
        if (memberDiscount != null
                && (memberDiscount.signum() <= 0 || memberDiscount.compareTo(BigDecimal.ONE) > 0)) {
            throw new BizException("会员折扣必须在 0 到 1 之间，例如 0.9 表示九折");
        }
    }
}
