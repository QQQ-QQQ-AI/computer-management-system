package com.cafe.service;
import com.cafe.common.BizException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real separate threads and committed transactions; delete only this test's fixtures. */
@SpringBootTest(properties="cafe.scheduling.enabled=false")
class ConcurrencyServiceTest extends TestFixtures {
    @Autowired PlatformTransactionManager manager;
    @BeforeEach void setup(){new TransactionTemplate(manager).executeWithoutResult(s->createFixtures());}
    @AfterEach void cleanup(){new TransactionTemplate(manager).executeWithoutResult(s->cleanupFixtures());}
    List<Boolean> pair(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            var one=pool.submit(task(a,ready,go));var two=pool.submit(task(b,ready,go));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        } finally {go.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    Callable<Boolean> task(Runnable work,CountDownLatch ready,CountDownLatch go){return ()->{ready.countDown();go.await();try{work.run();return true;}catch(BizException e){return false;}};}
    @Test void sameMemberDifferentSeatsOnlyOneOpens() throws Exception {
        var result=pair(()->sessions.open(first.getId(),seatA.getId(),null),()->sessions.open(first.getId(),seatB.getId(),null));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seat_session WHERE member_id=? AND status='USING'",Integer.class,first.getId()));
    }
    @Test void sameSeatDifferentMembersOnlyOneOpens() throws Exception {
        var result=pair(()->sessions.open(first.getId(),seatA.getId(),null),()->sessions.open(second.getId(),seatA.getId(),null));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
    }
    @Test void goodsAndCheckoutNeverLosePayment() throws Exception {
        var s=sessions.open(first.getId(),seatA.getId(),null);
        var result=pair(()->products.recordOrder(first.getId(),product.getId(),2,null,null),()->sessions.checkout(s.getId(),null));
        assertTrue(result.stream().allMatch(Boolean::booleanValue));
        var done=sessions.getById(s.getId());
        assertEquals(0,new BigDecimal("500").subtract(done.getHourFee()).subtract(new BigDecimal("6")).compareTo(members.getById(first.getId()).getBalance()));
        assertEquals(0,balances.checkConsistency(first.getId()).signum());
    }
    @Test void profileAndRechargePreserveFunds() throws Exception {
        assertTrue(pair(()->members.updateProfile(first.getId(),"updated",null,null),()->members.recharge(first.getId(),BigDecimal.TEN,"CASH",null)).stream().allMatch(Boolean::booleanValue));
        assertEquals(0,new BigDecimal("510").compareTo(members.getById(first.getId()).getBalance()));
        assertEquals(0,balances.checkConsistency(first.getId()).signum());
    }
    @Test void simultaneousRechargesBothCommit() throws Exception {
        assertTrue(pair(()->members.recharge(first.getId(),BigDecimal.TEN,"CASH",null),
                ()->members.recharge(first.getId(),BigDecimal.TEN,"ONLINE",null)).stream().allMatch(Boolean::booleanValue));
        assertEquals(0,new BigDecimal("520").compareTo(members.getById(first.getId()).getBalance()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM recharge_record WHERE member_id=?",Integer.class,first.getId()));
        assertEquals(0,balances.checkConsistency(first.getId()).signum());
    }
    @Test void duplicateCheckoutCannotChargeTwice() throws Exception {
        var s=sessions.open(first.getId(),seatA.getId(),null);
        assertEquals(1,pair(()->sessions.checkout(s.getId(),null),()->sessions.checkout(s.getId(),null)).stream().filter(Boolean::booleanValue).count());
        assertEquals(0,balances.checkConsistency(first.getId()).signum());
    }
    @Test void overlappingReservationsAreSerialized() throws Exception {
        var t=LocalDateTime.now().plusHours(1);
        assertEquals(1,pair(()->reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1)),()->reservations.create(second.getId(),seatA.getId(),t,t.plusHours(1))).stream().filter(Boolean::booleanValue).count());
    }
    @Test void lateCheckoutFailureRollsBackDebits() {
        var s=sessions.open(first.getId(),seatA.getId(),null);products.recordOrder(first.getId(),product.getId(),2,null,null);
        jdbc.update("UPDATE seat SET status='MAINTENANCE' WHERE id=?",seatA.getId());
        assertThrows(BizException.class,()->sessions.checkout(s.getId(),null));
        assertEquals("USING",sessions.getById(s.getId()).getStatus());
        assertEquals(0,new BigDecimal("500").compareTo(members.getById(first.getId()).getBalance()));
        assertEquals(0,balances.checkConsistency(first.getId()).signum());
    }
    @Test void failedVerificationLeavesReservationPending() {
        var t=LocalDateTime.now().minusMinutes(1);var r=reservations.create(first.getId(),seatA.getId(),t,t.plusHours(1));
        balances.decrease(first.getId(),"HOUR_FEE",new BigDecimal("500"),"TEST",null,"test",null);
        assertThrows(BizException.class,()->reservations.verify(r.getId(),null));
        assertEquals("PENDING",reservations.getById(r.getId()).getStatus());
        assertNull(sessions.findActiveByMember(first.getId()));assertEquals("FREE",seats.getById(seatA.getId()).getStatus());
    }
}
