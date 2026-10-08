package com.cafe.service;
import com.cafe.common.*;
import com.cafe.entity.*;
import com.cafe.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;

/** Fixtures are independent of the age and contents of demo data. */
abstract class TestFixtures {
    @Autowired MemberService members;
    @Autowired BalanceService balances;
    @Autowired SessionService sessions;
    @Autowired SeatService seats;
    @Autowired ProductService products;
    @Autowired EquipmentService equipment;
    @Autowired ReservationService reservations;
    @Autowired BillingRuleService rules;
    @Autowired NoticeService notices;
    @Autowired StatsService stats;
    @Autowired AuthService auth;
    @Autowired SysUserService users;
    @Autowired MemberMapper memberMapper;
    @Autowired SeatSessionMapper sessionMapper;
    @Autowired ReservationMapper reservationMapper;
    @Autowired ProductOrderMapper orderMapper;
    @Autowired EquipmentRentalMapper rentalMapper;
    @Autowired SysUserMapper userMapper;
    @Autowired JdbcTemplate jdbc;
    String tag, area;
    int level;
    Member first, second;
    Seat seatA, seatB;
    Product product;
    Equipment device;
    BillingRule rule;
    void createFixtures() {
        tag=UUID.randomUUID().toString().replace("-", "").substring(0,10); area="TEST_"+tag;
        level=jdbc.queryForObject("SELECT IFNULL(MAX(level),0)+1 FROM member_level",Integer.class);
        jdbc.update("INSERT INTO member_level(level,level_name,discount) VALUES(?,?,1.00)",level,area);
        first=makeMember("1"); second=makeMember("2");
        Seat a=new Seat(); a.setSeatNo("T"+tag+"A"); a.setArea(area); seatA=seats.create(a);
        Seat b=new Seat(); b.setSeatNo("T"+tag+"B"); b.setArea(area); seatB=seats.create(b);
        rule=rules.create(area,new BigDecimal("6"),5,new BigDecimal("25"),BigDecimal.ONE);
        product=products.create(area,new BigDecimal("3"),100);
        device=equipment.create("E"+tag,"测试外设",BigDecimal.ONE,new BigDecimal("20"));
    }
    private Member makeMember(String suffix) {
        Member m=new Member(); m.setCardNo("T"+tag+suffix); m.setName(area+suffix); m.setPhone("TEST"+tag+suffix);
        m.setPassword(PasswordUtil.encrypt("test123")); m.setBalance(BigDecimal.ZERO); m.setDeposit(BigDecimal.ZERO);
        m.setLevel(level); m.setStatus(1); memberMapper.insert(m);
        balances.increase(m.getId(),Constants.BAL_RECHARGE,new BigDecimal("500"),"TEST",null,"test",null);
        return members.getById(m.getId());
    }
    void cleanupFixtures() {
        if(first==null||second==null) return;
        for(String t:new String[]{"product_order","equipment_rental","seat_session","recharge_record","reservation","balance_record"})
            jdbc.update("DELETE FROM "+t+" WHERE member_id IN (?,?)",first.getId(),second.getId());
        jdbc.update("DELETE FROM member WHERE id IN (?,?)",first.getId(),second.getId());
        jdbc.update("DELETE FROM seat WHERE area=?",area); jdbc.update("DELETE FROM billing_rule WHERE area=?",area);
        jdbc.update("DELETE FROM product WHERE id=?",product.getId()); jdbc.update("DELETE FROM equipment WHERE id=?",device.getId());
        jdbc.update("DELETE FROM member_level WHERE level=?",level);
    }
}
