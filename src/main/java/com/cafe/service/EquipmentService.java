package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.Equipment;
import com.cafe.entity.EquipmentRental;
import com.cafe.entity.Member;
import com.cafe.mapper.EquipmentMapper;
import com.cafe.mapper.EquipmentRentalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备与租赁服务。
 *
 * 押金处理是本类的重点：租赁时从可用余额划转到冻结押金，归还时原额退回。
 * 押金不是消费，不能计入收入 —— 只有租金（rent_fee）才是收入，
 * 且按归还时间确认，因为租金在归还时才最终确定租期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentMapper equipmentMapper;
    private final EquipmentRentalMapper rentalMapper;
    private final MemberService memberService;
    private final BalanceService balanceService;

    /* ==================== 设备维护 ==================== */

    public List<Equipment> listAll() {
        return equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                .orderByAsc(Equipment::getCode));
    }

    /** 在库设备，用于租赁办理 */
    public List<Equipment> listAvailable() {
        return equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getStatus, Constants.EQUIP_IN_STOCK)
                .orderByAsc(Equipment::getCode));
    }

    public IPage<Equipment> page(long pageNo, long pageSize, String keyword, String status) {
        return equipmentMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Equipment>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(Equipment::getCode, keyword)
                                .or().like(Equipment::getCategory, keyword))
                        .eq(status != null && !status.isBlank(), Equipment::getStatus, status)
                        .orderByAsc(Equipment::getCode));
    }

    public Equipment getById(Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BizException("设备不存在，ID=" + id);
        }
        return equipment;
    }

    public EquipmentRental getRental(Long id) {
        EquipmentRental rental = rentalMapper.selectById(id);
        if (rental == null) {
            throw new BizException("租赁记录不存在，ID=" + id);
        }
        return rental;
    }

    @Transactional(rollbackFor = Exception.class)
    public Equipment create(String code, String category, BigDecimal rentPrice, BigDecimal deposit) {
        if (code == null || code.isBlank()) {
            throw new BizException("设备编号不能为空");
        }
        Long exists = equipmentMapper.selectCount(
                new LambdaQueryWrapper<Equipment>().eq(Equipment::getCode, code.trim()));
        if (exists != null && exists > 0) {
            throw new BizException("设备编号「" + code + "」已存在");
        }
        validateMoney(rentPrice, deposit);
        Equipment equipment = new Equipment();
        equipment.setCode(code.trim());
        equipment.setCategory(category == null ? "未分类" : category.trim());
        equipment.setRentPrice(rentPrice == null ? BigDecimal.ZERO : rentPrice);
        equipment.setDeposit(deposit == null ? BigDecimal.ZERO : deposit);
        equipment.setStatus(Constants.EQUIP_IN_STOCK);
        equipmentMapper.insert(equipment);
        return equipment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String code, String category,
                       BigDecimal rentPrice, BigDecimal deposit) {
        validateMoney(rentPrice, deposit);
        Equipment equipment = equipmentMapper.selectForUpdate(id);
        if (equipment == null) throw new BizException("设备不存在");
        if (code != null && !code.isBlank()) {
            equipment.setCode(code.trim());
        }
        if (category != null && !category.isBlank()) {
            equipment.setCategory(category.trim());
        }
        if (rentPrice != null) {
            equipment.setRentPrice(rentPrice);
        }
        if (deposit != null) {
            equipment.setDeposit(deposit);
        }
        equipmentMapper.updateById(equipment);
    }

    /** 变更设备状态（在库 / 维修中）。已租出的设备不允许手工改动状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (!Constants.EQUIP_IN_STOCK.equals(status) && !Constants.EQUIP_REPAIR.equals(status)) throw new BizException("设备状态无效");
        Equipment equipment = equipmentMapper.selectForUpdate(id);
        if (equipment == null) throw new BizException("设备不存在");
        if (Constants.EQUIP_RENTED.equals(equipment.getStatus())) {
            throw new BizException("设备「" + equipment.getCode()
                    + "」正在租借中，需先办理归还才能变更状态");
        }
        if (Constants.EQUIP_RENTED.equals(status)) {
            throw new BizException("「已租出」状态只能由租赁办理产生，不能手工设置");
        }
        equipment.setStatus(status);
        equipmentMapper.updateById(equipment);
        log.info("设备状态变更 设备={} -> {}", equipment.getCode(), status);
    }

    /* ==================== 租赁办理 ==================== */

    /**
     * 租借设备：冻结押金并将设备置为已租出。
     */
    @Transactional(rollbackFor = Exception.class)
    public EquipmentRental rent(Long memberId, Long equipmentId, Long operatorId) {
        Member member = memberService.lockById(memberId);
        if (member.getStatus() != null && member.getStatus() == Constants.DISABLED) {
            throw new BizException("该会员账户已冻结，无法办理租赁");
        }

        Equipment equipment = equipmentMapper.selectForUpdate(equipmentId);
        if (equipment == null) throw new BizException("设备不存在");
        if (!Constants.EQUIP_IN_STOCK.equals(equipment.getStatus())) {
            throw new BizException(String.format("设备「%s」当前为「%s」，不可出租",
                    equipment.getCode(), equipmentName(equipment.getStatus())));
        }

        BigDecimal deposit = nz(equipment.getDeposit());
        BigDecimal balance = nz(member.getBalance());
        if (balance.compareTo(deposit) < 0) {
            throw new BizException(String.format(
                    "余额不足：租赁「%s」需冻结押金 %s 元，当前可用余额 %s 元，还差 %s 元",
                    equipment.getCategory(), deposit.toPlainString(), balance.toPlainString(),
                    deposit.subtract(balance).toPlainString()));
        }

        // 先 CAS 抢占设备，失败说明被他人抢先，不产生任何其他副作用
        int rows = equipmentMapper.casStatus(equipmentId,
                Constants.EQUIP_IN_STOCK, Constants.EQUIP_RENTED);
        if (rows == 0) {
            throw new BizException("设备状态已被其他操作改变，请刷新后重试");
        }

        EquipmentRental rental = new EquipmentRental();
        rental.setMemberId(memberId);
        rental.setEquipmentId(equipmentId);
        rental.setRentTime(LocalDateTime.now());
        rental.setRentFee(BigDecimal.ZERO);
        rental.setDeposit(deposit);
        rental.setStatus(Constants.RENTAL_RENTING);
        rental.setOperatorId(operatorId);
        rentalMapper.insert(rental);

        // 冻结押金：可用余额减少、冻结押金等额增加，并写账户流水
        if (deposit.signum() > 0) balanceService.freezeDeposit(memberId, deposit, "RENTAL", rental.getId(),
                "设备租赁押金冻结：" + equipment.getCode(), operatorId);

        log.info("设备租借成功 会员={} 设备={} 冻结押金={} 经办={}",
                member.getCardNo(), equipment.getCode(), deposit, operatorId);
        return rental;
    }

    /**
     * 归还设备：解冻押金并按租期扣收租金。
     *
     * 先解冻再扣租金：押金退回后余额才够扣租金，
     * 顺序颠倒会导致正常归还因余额不足而失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public EquipmentRental returnEquipment(Long rentalId, Long operatorId) {
        EquipmentRental initial = getRental(rentalId);
        memberService.lockById(initial.getMemberId());
        EquipmentRental rental = rentalMapper.selectForUpdate(rentalId);
        if (!Constants.RENTAL_RENTING.equals(rental.getStatus())) {
            throw new BizException("该租赁记录已归还，请勿重复操作");
        }
        Equipment equipment = equipmentMapper.selectForUpdate(rental.getEquipmentId());

        LocalDateTime now = LocalDateTime.now();
        long days = rentalDays(rental.getRentTime(), now);
        BigDecimal rentFee = nz(equipment.getRentPrice()).multiply(BigDecimal.valueOf(days));

        if (nz(rental.getDeposit()).signum() > 0) balanceService.unfreezeDeposit(rental.getMemberId(), nz(rental.getDeposit()),
                "RENTAL", rentalId, "设备归还押金解冻：" + equipment.getCode(), operatorId);

        if (rentFee.signum() > 0) {
            balanceService.decrease(rental.getMemberId(), Constants.BAL_RENT_FEE, rentFee,
                    "RENTAL", rentalId,
                    String.format("设备租金：%s 共 %d 天 × %s 元",
                            equipment.getCode(), days, equipment.getRentPrice().toPlainString()),
                    operatorId);
        }

        rental.setReturnTime(now);
        rental.setRentFee(rentFee);
        rental.setStatus(Constants.RENTAL_RETURNED);
        rental.setOperatorId(operatorId);
        rentalMapper.updateById(rental);

        int rows = equipmentMapper.casStatus(rental.getEquipmentId(),
                Constants.EQUIP_RENTED, Constants.EQUIP_IN_STOCK);
        if (rows == 0) {
            throw new BizException("设备「" + equipment.getCode() + "」状态异常，归还失败，请联系管理员");
        }

        log.info("设备归还成功 会员={} 设备={} 租期={}天 租金={} 解冻押金={}",
                rental.getMemberId(), equipment.getCode(), days, rentFee, rental.getDeposit());
        return rental;
    }

    /* ==================== 查询 ==================== */

    public IPage<EquipmentRental> pageRentals(long pageNo, long pageSize,
                                              Long memberId, String status) {
        return rentalMapper.selectDetailPage(new Page<>(pageNo, pageSize), memberId, status);
    }

    /* ==================== 内部工具 ==================== */

    /** 租期按天向上取整，不足一天按一天计 */
    private long rentalDays(LocalDateTime from, LocalDateTime to) {
        long seconds = Duration.between(from, to).getSeconds();
        if (seconds <= 0) {
            return 1;
        }
        return Math.max(1, (long) Math.ceil(seconds / 86400.0));
    }

    private void validateMoney(BigDecimal rentPrice, BigDecimal deposit) {
        for (BigDecimal value : new BigDecimal[]{rentPrice, deposit}) {
            if (value != null && (value.signum() < 0 || value.scale() > 2)) throw new BizException("租金与押金必须非负且最多两位小数");
        }
    }

    private String equipmentName(String status) {
        return switch (status) {
            case Constants.EQUIP_IN_STOCK -> "在库";
            case Constants.EQUIP_RENTED -> "已租出";
            case Constants.EQUIP_REPAIR -> "维修中";
            default -> status;
        };
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
