package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Synchronized per-execution reference ledger; M7 replaces it with transactional durable accounting. */
public final class InMemoryResourceBudgetLedger implements ResourceBudgetLedger{
    private static final class Account{
        final ResourceBudget budget;final EnumMap<ResourceUnit,Long> consumed=new EnumMap<>(ResourceUnit.class);
        final Map<String,BudgetReservation> reservations=new HashMap<>();Account(ResourceBudget b){budget=b;}
    }
    private final ConcurrentHashMap<String,Account> accounts=new ConcurrentHashMap<>();
    public void register(String executionId,ResourceBudget budget){String id=require(executionId);if(accounts.putIfAbsent(id,new Account(Objects.requireNonNull(budget)))!=null)throw new IllegalStateException("budget already registered for execution "+id);}
    public BudgetReservation reserve(String executionId,String reservationId,ResourceUsage requested,Instant at){
        var account=account(executionId);synchronized(account){requireBeforeDeadline(account,at);var previous=account.reservations.get(reservationId);
            if(previous!=null){if(!previous.reserved().equals(requested))throw new IllegalStateException("reservation ID reused with different amount");if(previous.status()==ReservationStatus.RELEASED)throw new IllegalStateException("released reservation ID cannot be reused");return previous;}
            for(var unit:ResourceUnit.values()){
                long limit=account.budget.limit(unit);
                if(limit==Long.MAX_VALUE)continue; // Long.MAX_VALUE is the explicit unlimited sentinel.
                long current=account.consumed.getOrDefault(unit,0L);
                long available=limit-current;
                if(current>limit)throw new ResourceBudgetExceededException("resource budget already exceeded for "+unit);
                long inFlight=reservedFor(account,unit);
                if(inFlight>available)throw new ResourceBudgetExceededException("reserved work already exhausts budget for "+unit);
                available-=inFlight;
                if(requested.amount(unit)>available)throw new ResourceBudgetExceededException("resource budget exceeded for "+unit);
            }
            var reservation=new BudgetReservation(reservationId,executionId,requested,ReservationStatus.ACTIVE,at,null);account.reservations.put(reservationId,reservation);return reservation;
        }
    }
    public ResourceBudgetSnapshot commit(String executionId,String reservationId,ResourceUsage actual,Instant at){
        var account=account(executionId);synchronized(account){
            var r=account.reservations.get(reservationId);
            if(r==null)throw new IllegalStateException("reservation not found: "+reservationId);
            if(r.status()==ReservationStatus.COMMITTED){if(!r.actualUsage().equals(actual))throw new IllegalStateException("committed reservation cannot be charged with different actual usage");return snapshotLocked(executionId,account,at);}
            if(r.status()!=ReservationStatus.ACTIVE)throw new IllegalStateException("released reservation cannot be committed");
            // Record actual consumption even when it overruns the reservation; silently discarding it would permit unsafe reuse.
            for(var u:ResourceUnit.values())account.consumed.compute(u,(ignored,current)->saturatedAdd(current==null?0L:current,actual.amount(u)));
            account.reservations.put(reservationId,new BudgetReservation(r.id(),r.executionId(),r.reserved(),ReservationStatus.COMMITTED,r.createdAt(),actual));return snapshotLocked(executionId,account,at);}
    }
    public ResourceBudgetSnapshot release(String executionId,String reservationId,Instant at){var account=account(executionId);synchronized(account){
        var r=account.reservations.get(reservationId);if(r==null)throw new IllegalStateException("reservation not found: "+reservationId);
        if(r.status()==ReservationStatus.RELEASED)return snapshotLocked(executionId,account,at);
        if(r.status()!=ReservationStatus.ACTIVE)throw new IllegalStateException("committed reservation cannot be released");
        account.reservations.put(reservationId,new BudgetReservation(r.id(),r.executionId(),r.reserved(),ReservationStatus.RELEASED,r.createdAt(),null));return snapshotLocked(executionId,account,at);}}
    public ResourceBudgetSnapshot snapshot(String executionId,Instant at){var account=account(executionId);synchronized(account){return snapshotLocked(executionId,account,at);}}
    private ResourceBudgetSnapshot snapshotLocked(String id,Account a,Instant at){
        var reserved=new EnumMap<ResourceUnit,Long>(ResourceUnit.class);
        a.reservations.values().stream().filter(r->r.status()==ReservationStatus.ACTIVE).forEach(r->java.util.Arrays.stream(ResourceUnit.values()).forEach(u->reserved.compute(u,(ignored,current)->saturatedAdd(current==null?0L:current,r.reserved().amount(u)))));
        return new ResourceBudgetSnapshot(id,a.budget,a.consumed,reserved,at);
    }
    private static long reservedFor(Account account,ResourceUnit unit){
        long total=0L;
        for(BudgetReservation reservation:account.reservations.values()){
            if(reservation.status()==ReservationStatus.ACTIVE)total=saturatedAdd(total,reservation.reserved().amount(unit));
        }
        return total;
    }
    private static long saturatedAdd(long left,long right){
        if(right>Long.MAX_VALUE-left)return Long.MAX_VALUE;
        return left+right;
    }
    private static BudgetReservation requireActive(Account a,String id){var r=a.reservations.get(id);if(r==null||r.status()!=ReservationStatus.ACTIVE)throw new IllegalStateException("active reservation not found: "+id);return r;}
    private Account account(String id){var a=accounts.get(require(id));if(a==null)throw new IllegalArgumentException("budget not registered: "+id);return a;}
    private static void requireBeforeDeadline(Account a,Instant at){Objects.requireNonNull(at,"at");if(!at.isBefore(a.budget.deadline()))throw new ResourceBudgetExceededException("execution deadline exceeded");}
    private static String require(String v){String s=Objects.requireNonNull(v,"executionId").trim();if(s.isEmpty())throw new IllegalArgumentException("executionId cannot be blank");return s;}
}
