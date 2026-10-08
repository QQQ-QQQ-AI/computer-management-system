package com.cafe.service;
import com.cafe.common.*;
import com.cafe.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="cafe.scheduling.enabled=false")
@Transactional
class SessionServiceTest extends TestFixtures {
    @BeforeEach void setup(){createFixtures();}
    @Test void ordinaryOpenConsumesOwnCurrentReservation(){
        var t=LocalDateTime.now().minusMinutes(1);
        var r=reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1));
        open();assertEquals("USED",reservations.getById(r.getId()).getStatus());
        jdbc.update("UPDATE reservation SET start_time=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE id=?",r.getId());
        reservations.expireTimeout();assertEquals("USED",reservations.getById(r.getId()).getStatus());
    }
    @Test void openCardRejectsShortPassword(){
        String phone="139"+String.format("%08d",Math.floorMod(tag.hashCode(),100000000));
        var error=assertThrows(BizException.class,()->members.openCard("test","11010519491231002X",phone,level,"123",BigDecimal.ZERO,null));
        assertTrue(error.getMessage().contains("密码"));assertNull(members.getByPhone(phone));
    }
    SeatSession open(){return sessions.open(first.getId(),seatA.getId(),null);}
    void ledger(){assertEquals(0,balances.checkConsistency(first.getId()).signum());}
    @Test void checkoutAndLedger(){
        var s=open();s.setStartTime(LocalDateTime.now().minusHours(3).plusSeconds(30));sessionMapper.updateById(s);
        var done=sessions.checkout(s.getId(),null);assertEquals(180,done.getDurationMinutes());
        assertEquals(0,new BigDecimal("18").compareTo(done.getHourFee()));assertEquals("FREE",seats.getById(seatA.getId()).getStatus());ledger();
    }
    @Test void rejectRepeatedCheckout(){var s=open();sessions.checkout(s.getId(),null);assertThrows(BizException.class,()->sessions.checkout(s.getId(),null));}
    @Test void rejectOccupiedSeat(){open();assertThrows(BizException.class,()->sessions.open(second.getId(),seatA.getId(),null));}
    @Test void rejectMaintenance(){seats.transit(seatA.getId(),"MAINTENANCE","test");assertThrows(BizException.class,this::open);}
    @Test void rejectManualUsingAndRelease(){
        assertThrows(BizException.class,()->seats.transit(seatA.getId(),"USING","test"));open();
        assertThrows(BizException.class,()->seats.transit(seatA.getId(),"FREE","test"));
        assertThrows(BizException.class,()->seats.transit(seatA.getId(),"MAINTENANCE","test"));
    }
    @Test void rejectOverlap(){var t=LocalDateTime.now().plusHours(1);reservations.create(first.getId(),seatA.getId(),t,t.plusHours(2));assertThrows(BizException.class,()->reservations.create(second.getId(),seatA.getId(),t.plusMinutes(30),t.plusHours(1)));}
    @Test void allowExactAdjacent(){var t=LocalDateTime.now().plusHours(1).withNano(0);reservations.create(first.getId(),seatA.getId(),t,t.plusHours(2));assertNotNull(reservations.create(second.getId(),seatA.getId(),t.plusHours(2),t.plusHours(3)).getId());}
    @Test void rejectZeroBalance(){balances.decrease(first.getId(),"HOUR_FEE",new BigDecimal("500"),"TEST",null,"test",null);assertThrows(BizException.class,this::open);}
    @Test void reservedSeatRejectsOthers(){var t=LocalDateTime.now().minusMinutes(1);reservations.create(second.getId(),seatA.getId(),t,t.plusHours(1));assertThrows(BizException.class,this::open);}
    @Test void verifyOpensOriginalSeat(){var t=LocalDateTime.now().minusMinutes(1);var r=reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1));reservations.verify(r.getId(),null);assertEquals(seatA.getId(),sessions.findActiveByMember(first.getId()).getSeatId());assertEquals("USED",reservations.getById(r.getId()).getStatus());}
    @Test void gracePeriod(){var t=LocalDateTime.now().minusMinutes(5);var r=reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1));reservations.expireTimeout();assertEquals("PENDING",reservations.getById(r.getId()).getStatus());jdbc.update("UPDATE reservation SET start_time=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE id=?",r.getId());reservations.expireTimeout();assertEquals("EXPIRED",reservations.getById(r.getId()).getStatus());}
    @Test void rejectEarlyVerify(){var t=LocalDateTime.now().plusHours(2);var r=reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1));assertThrows(BizException.class,()->reservations.verify(r.getId(),null));}
    @Test void zeroDepositWorks(){equipment.update(device.getId(),null,null,BigDecimal.ONE,BigDecimal.ZERO);var r=equipment.rent(first.getId(),device.getId(),null);equipment.returnEquipment(r.getId(),null);ledger();}
    @Test void rejectNegativePrices(){assertThrows(BizException.class,()->equipment.update(device.getId(),null,null,new BigDecimal("-1"),BigDecimal.ZERO));assertThrows(BizException.class,()->equipment.create("NEG"+tag,"test",BigDecimal.ONE,new BigDecimal("-1")));}
    @Test void rejectNegativeStock(){assertThrows(BizException.class,()->products.create(tag,BigDecimal.ONE,-1));}
    @Test void profilePasswordStatusPreserveMoney(){members.recharge(first.getId(),BigDecimal.TEN,"CASH",null);members.updateProfile(first.getId(),"new",null,null);members.resetPassword(first.getId(),"new123");members.changeStatus(first.getId(),0);ledger();assertEquals(0,new BigDecimal("510").compareTo(members.getById(first.getId()).getBalance()));}
    @Test void frozenMemberCanCheckout(){var s=open();products.recordOrder(first.getId(),product.getId(),1,null,null);members.changeStatus(first.getId(),0);assertDoesNotThrow(()->sessions.checkout(s.getId(),null));ledger();}
    @Test void frozenMemberCanReturn(){var r=equipment.rent(first.getId(),device.getId(),null);members.changeStatus(first.getId(),0);assertDoesNotThrow(()->equipment.returnEquipment(r.getId(),null));ledger();}
    @Test void frozenMemberCannotStartBusiness(){members.changeStatus(first.getId(),0);assertThrows(BizException.class,this::open);assertThrows(BizException.class,()->equipment.rent(first.getId(),device.getId(),null));assertThrows(BizException.class,()->products.recordOrder(first.getId(),product.getId(),1,null,null));}
    @Test void goodsIncluded(){var s=open();products.recordOrder(first.getId(),product.getId(),2,null,null);assertEquals(0,new BigDecimal("6").compareTo(sessions.checkout(s.getId(),null).getProductFee()));ledger();}
    @Test void rejectInvalidSessionGoods(){var s=open();assertThrows(BizException.class,()->products.recordOrder(second.getId(),product.getId(),1,s.getId(),null));sessions.checkout(s.getId(),null);assertThrows(BizException.class,()->products.recordOrder(first.getId(),product.getId(),1,s.getId(),null));}
    @Test void removePackage(){rules.update(rule.getId(),new BigDecimal("6"),null,null,BigDecimal.ONE,null);assertNull(rules.getById(rule.getId()).getPackageHours());assertNull(rules.getById(rule.getId()).getPackagePrice());}
    @Test void deleteNotice(){var n=notices.publish(tag,"test",null);notices.delete(n.getId(),null);assertTrue(notices.listPublished().stream().noneMatch(v->v.getId().equals(n.getId())));assertThrows(BizException.class,()->notices.changeStatus(n.getId(),1));assertEquals(-1,jdbc.queryForObject("SELECT status FROM notice WHERE id=?",Integer.class,n.getId()));}
    @Test void historicalDates(){LocalDate d=LocalDate.of(2020,1,2);var s=open();products.recordOrder(first.getId(),product.getId(),2,null,null);sessions.checkout(s.getId(),null);jdbc.update("UPDATE seat_session SET end_time=? WHERE id=?",d.atTime(12,0),s.getId());var trend=stats.trend(d,d);assertEquals(1,trend.size());assertEquals("01-02",trend.get(0).get("label"));assertTrue(stats.topProducts(20,d,d).stream().anyMatch(p->area.equals(p.get("name"))));assertTrue(stats.topProducts(20,d.plusDays(1),d.plusDays(1)).stream().noneMatch(p->area.equals(p.get("name"))));}
    @Test void migrateLegacyPassword(){jdbc.update("UPDATE member SET password=? WHERE id=?",Md5Util.encrypt("test123"),first.getId());assertNotNull(auth.loginMember(first.getPhone(),"test123").getCredentialStamp());String hash=members.getById(first.getId()).getPassword();assertTrue(hash.startsWith("$2"));assertTrue(PasswordUtil.matches("test123",hash));assertFalse(PasswordUtil.matches("wrong",hash));ledger();}
}
