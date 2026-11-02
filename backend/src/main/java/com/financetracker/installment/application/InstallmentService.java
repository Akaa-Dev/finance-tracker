package com.financetracker.installment.application;
import com.financetracker.installment.api.Contracts.*;
import com.financetracker.installment.persistence.*;
import com.financetracker.installment.domain.FixedSchedule;
import com.financetracker.account.persistence.CashAccount;
import com.financetracker.auth.Owner;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
@Service
@Transactional(readOnly=true)
public class InstallmentService {
    private final EntityManager em; private final ObjectMapper json;
    public InstallmentService(EntityManager em,ObjectMapper json) { this.em=em;this.json=json; }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,message); }
    private InstallmentPlan plan(UUID owner,UUID id,boolean lock) {
        var p=em.find(InstallmentPlan.class,id,lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if(p==null || !p.userId.equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found.");
        return p;
    }
    private List<InstallmentDue> dues(UUID id) {
        return em.createQuery("from InstallmentDue where planId=:id order by dueDate,sequenceNumber",InstallmentDue.class).setParameter("id",id).getResultList();
    }
    private List<InstallmentPayment> payments(UUID id) {
        return em.createQuery("from InstallmentPayment where planId=:id order by createdAt,id",InstallmentPayment.class).setParameter("id",id).getResultList();
    }
    private List<PaymentAllocation> allocations(UUID id) {
        return em.createQuery("from PaymentAllocation where planId=:id",PaymentAllocation.class).setParameter("id",id).getResultList();
    }
    public Plan get(UUID owner,UUID id) { return snapshot(plan(owner,id,false)); }
    public Page list(UUID owner,int page,int size,String search) {
        if(page<0 || page>100000 || size<1 || size>100 || search.length()>120) throw invalid("Invalid pagination or search.");
        String where=" where userId=:owner and locate(:search,lower(name))>0";
        var items=em.createQuery("from InstallmentPlan"+where+" order by createdAt desc,id",InstallmentPlan.class)
            .setParameter("owner",owner).setParameter("search",search.toLowerCase(Locale.ROOT)).setFirstResult(page*size).setMaxResults(size)
            .getResultList().stream().map(this::snapshot).toList();
        long total=em.createQuery("select count(p) from InstallmentPlan p"+where,Long.class)
            .setParameter("owner",owner).setParameter("search",search.toLowerCase(Locale.ROOT)).getSingleResult();
        return new Page(items,page,size,total);
    }
    @Transactional public Plan create(UUID owner,NewPlan request) {
        if(request.principalAmount()!=null && request.principalAmount().compareTo(request.totalPayable())>0)
            throw invalid("Principal must not exceed total payable.");
        List<FixedSchedule.Due> schedule;
        try { schedule=FixedSchedule.generate(request.totalPayable(),request.installmentCount(),request.firstDueDate()); }
        catch(IllegalArgumentException | DateTimeException e) { throw invalid("Invalid schedule; each due must be at least EUR 0.01."); }
        var p=new InstallmentPlan();p.id=UUID.randomUUID();p.userId=owner;p.name=request.name().strip();p.provider=request.provider().strip();
        p.description=request.description();p.currency="EUR";p.principalAmount=request.principalAmount()==null ? null : request.principalAmount().setScale(2);p.totalPayable=request.totalPayable().setScale(2);
        p.installmentCount=request.installmentCount();p.firstDueDate=request.firstDueDate();p.administrativeStatus="ACTIVE";p.createdAt=Instant.now();em.persist(p);
        for(var due:schedule) { var d=new InstallmentDue();d.id=UUID.randomUUID();d.userId=owner;d.planId=p.id;d.sequenceNumber=due.sequenceNumber();
            d.dueDate=due.dueDate();d.amount=due.amount();em.persist(d); }
        em.flush();return snapshot(p);
    }
    // Always lock owner then plan: owner-scoped keys cannot race across different plans.
    @Transactional public String pay(UUID owner,UUID id,UUID key,NewPayment request) {
        em.find(Owner.class,owner,LockModeType.PESSIMISTIC_WRITE);
        var p=plan(owner,id,true);
        String fingerprint=fingerprint(id,request);
        var existing=em.createQuery("from InstallmentPayment where userId=:owner and idempotencyKey=:key",InstallmentPayment.class)
            .setParameter("owner",owner).setParameter("key",key).getResultList();
        if(!existing.isEmpty()) {
            var prior=existing.getFirst();
            if(!prior.requestFingerprint.equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different request.");
            return prior.receipt;
        }
        if(!p.administrativeStatus.equals("ACTIVE")) throw invalid("Plan is not active.");
        var account=em.find(CashAccount.class,request.accountId());
        if(account==null || !account.userId.equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found.");
        if(request.paidOn().isBefore(account.openingDate) || request.paidOn().isAfter(LocalDate.now())) throw invalid("Payment date must be between account opening and today.");
        Plan before=snapshot(p);
        List<BigDecimal> amounts;
        try { amounts=FixedSchedule.allocate(request.amount(),before.schedule().stream().map(Due::remaining).toList()); }
        catch(IllegalArgumentException e) { throw invalid("Payment exceeds outstanding or has invalid amount."); }
        var payment=new InstallmentPayment();payment.id=UUID.randomUUID();payment.userId=owner;payment.planId=id;payment.accountId=request.accountId();
        payment.amount=request.amount().setScale(2);payment.paidOn=request.paidOn();payment.note=request.note();payment.idempotencyKey=key;
        payment.requestFingerprint=fingerprint;payment.createdAt=Instant.now();payment.receipt="";em.persist(payment);
        List<Allocation> result=new ArrayList<>();
        for(int i=0;i<amounts.size();i++) if(amounts.get(i).signum()>0) {
            var due=before.schedule().get(i);var a=new PaymentAllocation();a.id=UUID.randomUUID();a.userId=owner;a.planId=id;
            a.paymentId=payment.id;a.dueId=due.id();a.amount=amounts.get(i);em.persist(a);
            result.add(new Allocation(a.dueId,a.amount,due.remaining().subtract(a.amount)));
        }
        payment.receipt=json.writeValueAsString(new Receipt(payment.id,id,payment.amount,List.copyOf(result),
            before.totalPaid().add(payment.amount),before.outstandingBalance().subtract(payment.amount)));
        em.flush();return payment.receipt;
    }
    @Transactional public Plan voidPayment(UUID owner,UUID id,UUID paymentId) {
        em.find(Owner.class,owner,LockModeType.PESSIMISTIC_WRITE);var p=plan(owner,id,true);
        var payment=em.find(InstallmentPayment.class,paymentId);
        if(payment==null || !payment.userId.equals(owner) || !payment.planId.equals(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found.");
        if(payment.voidedAt==null) payment.voidedAt=Instant.now();
        em.flush();return snapshot(p);
    }
    private String fingerprint(UUID id,NewPayment r) {
        try {
            String canonical=json.writeValueAsString(List.of(id.toString(),r.accountId().toString(),r.amount().setScale(2).toPlainString(),r.paidOn().toString(),r.note()==null ? "" : r.note()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Plan snapshot(InstallmentPlan p) {
        var payments=payments(p.id);var all=allocations(p.id);var valid=new HashSet<UUID>();BigDecimal paid=new BigDecimal("0.00");
        for(var payment:payments) if(payment.voidedAt==null) { valid.add(payment.id);paid=paid.add(payment.amount); }
        Map<UUID,BigDecimal> allocated=new HashMap<>();
        for(var a:all) if(valid.contains(a.paymentId)) allocated.merge(a.dueId,a.amount,BigDecimal::add);
        var today=LocalDate.now();List<Due> schedule=new ArrayList<>();BigDecimal overdue=new BigDecimal("0.00");LocalDate next=null;
        for(var d:dues(p.id)) {
            var used=allocated.getOrDefault(d.id,new BigDecimal("0.00"));var remaining=d.amount.subtract(used);
            boolean partial=used.signum()>0 && remaining.signum()>0;
            String status=remaining.signum()==0 ? "PAID" : d.dueDate.isBefore(today) ? "OVERDUE" : partial ? "PARTIALLY_PAID" : d.dueDate.equals(today) ? "DUE" : "UPCOMING";
            if(remaining.signum()>0) { if(next==null) next=d.dueDate;if(d.dueDate.isBefore(today)) overdue=overdue.add(remaining); }
            schedule.add(new Due(d.id,d.sequenceNumber,d.dueDate,d.amount,used,remaining,status,partial));
        }
        List<Payment> history=payments.stream().map(x->new Payment(x.id,x.accountId,x.paidOn,x.amount,x.note,x.createdAt,x.voidedAt,
            all.stream().filter(a->a.paymentId.equals(x.id)).sorted(Comparator.comparing(a->schedule.stream().filter(d->d.id().equals(a.dueId)).findFirst().orElseThrow().sequenceNumber()))
                .map(a->new Allocation(a.dueId,a.amount,schedule.stream().filter(d->d.id().equals(a.dueId)).findFirst().orElseThrow().remaining())).toList())).toList();
        var outstanding=p.totalPayable.subtract(paid);
        return new Plan(p.id,p.name,p.provider,p.description,p.currency,p.principalAmount,p.totalPayable,p.installmentCount,p.firstDueDate,
            p.administrativeStatus,paid,outstanding,paid.multiply(new BigDecimal("100")).divide(p.totalPayable,2,RoundingMode.HALF_UP),next,overdue,
            schedule.getLast().dueDate(),outstanding.signum()==0 ? "PAID" : overdue.signum()>0 ? "OVERDUE" : "ACTIVE",List.copyOf(schedule),history);
    }
}
