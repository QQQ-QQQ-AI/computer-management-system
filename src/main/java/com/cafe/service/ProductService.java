package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.Member;
import com.cafe.entity.Product;
import com.cafe.entity.ProductOrder;
import com.cafe.entity.SeatSession;
import com.cafe.mapper.ProductMapper;
import com.cafe.mapper.ProductOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务：商品维护与商品记账。
 *
 * 关于扣款时点，区分两种情形，这样「收入」与「收款」才不会错位：
 *
 * 1. 会员正在上机 → 商品挂在该机位上，下机结算时与上机费一并扣款（挂账）。
 *    此时只扣库存、不扣余额，也不产生收入；
 *
 * 2. 会员未上机（如直接到前台买饮料）→ 当场扣款结清，立即产生收入。
 *
 * 因此统计商品收入时不能只看记账时间：挂账部分应以关联上机记录的结算时间为准，
 * 否则会出现「钱还没收就记成收入」。对应的 SQL 见 StatsMapper.xml。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductOrderMapper productOrderMapper;
    private final MemberService memberService;
    private final SessionService sessionService;
    private final BalanceService balanceService;

    /* ==================== 商品维护 ==================== */

    public IPage<Product> page(long pageNo, long pageSize, String keyword, Integer status) {
        return productMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Product>()
                        .like(keyword != null && !keyword.isBlank(), Product::getName, keyword)
                        .eq(status != null, Product::getStatus, status)
                        .orderByAsc(Product::getId));
    }

    /** 上架中的商品，用于商品记账与会员端展示 */
    public List<Product> listOnSale() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, Constants.ENABLED)
                .orderByAsc(Product::getId));
    }

    public List<Product> listAll() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>().orderByAsc(Product::getId));
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("商品不存在，ID=" + id);
        }
        return product;
    }

    @Transactional(rollbackFor = Exception.class)
    public Product create(String name, BigDecimal price, Integer stock) {
        if (name == null || name.isBlank()) {
            throw new BizException("商品名称不能为空");
        }
        if (price == null || price.signum() < 0) {
            throw new BizException("商品单价不能为负数");
        }
        if (stock != null && stock < 0) throw new BizException("库存不能为负数");
        if (price.scale() > 2) throw new BizException("价格最多保留两位小数");
        Product product = new Product();
        product.setName(name.trim());
        product.setPrice(price);
        product.setStock(stock == null ? 0 : stock);
        product.setStatus(Constants.ENABLED);
        productMapper.insert(product);
        return product;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, BigDecimal price, Integer stock, Integer status) {
        Product product = productMapper.selectForUpdate(id);
        if (product == null) throw new BizException("商品不存在");
        if (name != null && !name.isBlank()) {
            product.setName(name.trim());
        }
        if (price != null) {
            if (price.scale() > 2) throw new BizException("价格最多保留两位小数");
            if (price.signum() < 0) {
                throw new BizException("商品单价不能为负数");
            }
            product.setPrice(price);
        }
        if (stock != null) {
            if (stock < 0) {
                throw new BizException("库存不能为负数");
            }
            product.setStock(stock);
        }
        if (status != null) {
            product.setStatus(status);
        }
        productMapper.updateById(product);
    }

    /** 上架 / 下架 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, int status) {
        if (status != Constants.ENABLED && status != Constants.DISABLED) throw new BizException("商品状态无效");
        Product product = productMapper.selectForUpdate(id);
        if (product == null) throw new BizException("商品不存在");
        product.setStatus(status);
        productMapper.updateById(product);
        log.info("商品状态变更 商品={} -> {}", product.getName(), status);
    }

    /* ==================== 商品记账 ==================== */

    /**
     * 商品记账。
     *
     * @param sessionId 指定挂账的上机记录ID；传 null 时由系统自动判断会员是否正在上机
     * @return 生成的记账记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder recordOrder(Long memberId, Long productId, int quantity,
                                    Long sessionId, Long operatorId) {
        if (quantity <= 0) {
            throw new BizException("购买数量必须大于 0");
        }
        Member member = memberService.lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，无法消费");
        }
        SeatSession active = sessionId == null ? sessionService.lockActiveByMember(memberId) : sessionService.lockSession(sessionId);
        if (active != null && (!memberId.equals(active.getMemberId()) || !Constants.SESSION_USING.equals(active.getStatus()))) {
            throw new BizException("上机记录已结算或不属于该会员，无法挂账");
        }
        Long effectiveSessionId = active == null ? null : active.getId();
        Product product = productMapper.selectForUpdate(productId);
        if (product == null) throw new BizException("商品不存在");
        if (product.getStatus() == null || product.getStatus() != Constants.ENABLED) {
            throw new BizException("商品「" + product.getName() + "」已下架，无法销售");
        }

        // 扣减库存：带库存条件的 UPDATE，库存不足时受影响行数为 0，
        // 由数据库保证「判断库存」与「扣减库存」的原子性，避免并发超卖
        int rows = productMapper.deductStock(productId, quantity);
        if (rows == 0) {
            throw new BizException(String.format("商品「%s」库存不足：当前库存 %d，本次需要 %d",
                    product.getName(), product.getStock() == null ? 0 : product.getStock(), quantity));
        }

        BigDecimal amount = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        ProductOrder order = new ProductOrder();
        order.setSessionId(effectiveSessionId);
        order.setMemberId(memberId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setAmount(amount);
        order.setOperatorId(operatorId);
        productOrderMapper.insert(order);

        if (effectiveSessionId == null && amount.signum() > 0) {
            // 未上机：当场结清，立即产生收入
            balanceService.decrease(memberId, Constants.BAL_PRODUCT, amount,
                    "PRODUCT_ORDER", order.getId(),
                    "商品消费：" + product.getName() + " × " + quantity, operatorId);
            log.info("商品当场结清 会员={} 商品={} 数量={} 金额={}",
                    member.getCardNo(), product.getName(), quantity, amount);
        } else {
            // 上机中：挂账，下机结算时一并扣款
            log.info("商品挂账 会员={} 商品={} 数量={} 金额={} 挂至记录={}",
                    member.getCardNo(), product.getName(), quantity, amount, effectiveSessionId);
        }
        return order;
    }

    /** 分页查询商品消费明细 */
    public IPage<ProductOrder> pageOrders(long pageNo, long pageSize, Long memberId, Long sessionId) {
        return productOrderMapper.selectDetailPage(new Page<>(pageNo, pageSize), memberId, sessionId);
    }

    /** 某条上机记录下的商品消费明细，会员端「上机费用查询」使用 */
    public List<ProductOrder> listBySession(Long sessionId) {
        return productOrderMapper.selectDetailPage(new Page<>(1, 100), null, sessionId).getRecords();
    }

    /** 会员历史累计商品消费，供会员端展示累计消费 */
    public BigDecimal sumAmountByMember(Long memberId) {
        BigDecimal sum = productOrderMapper.sumAmountByMember(memberId);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
