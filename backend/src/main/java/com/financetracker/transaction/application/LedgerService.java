package com.financetracker.transaction.application;
import com.financetracker.transaction.api.LedgerContracts.*;
import com.financetracker.transaction.persistence.*;
import com.financetracker.recurrence.persistence.ActualLink;
import com.financetracker.recurrence.application.PlanningService;
import com.financetracker.account.persistence.CashAccount;
import com.financetracker.common.FinancialWrites;
import static com.financetracker.common.FinancialWrites.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class LedgerService {
    private final EntityManager em;private final FinancialWrites writes;private final PlanningService planning;
    public LedgerService(EntityManager em,FinancialWrites writes,PlanningService planning) { this.em=em;this.writes=writes;this.planning=planning; }
    private ActualTransaction actual(UUID owner,UUID id) { var a=em.find(ActualTransaction.class,id);if(a==null || !a.userId.equals(owner)) throw missing();return a; }
    private List<ActualLink> links(UUID id) { return em.createQuery("from ActualLink where transactionId=:id order by linkedAt,id",ActualLink.class).setParameter("id",id).getResultList(); }
    private Actual dto(ActualTransaction a) {
        return new Actual(a.id,a.accountId,a.categoryId,a.direction,a.amount,a.transactionDate,a.note,a.createdAt,a.voidedAt,a.voidReason,a.supersedesId,
            links(a.id).stream().map(l->new LinkView(l.id,l.occurrenceId,l.plannedCashflowId,l.linkedAt,l.unlinkedAt,l.unlinkReason)).toList());
    }
    public Actual get(UUID owner,UUID id) { return dto(actual(owner,id)); }
    public List<CategoryView> categories(UUID owner) { return em.createQuery("from Category where userId=:owner order by kind,name",Category.class).setParameter("owner",owner).getResultList().stream().map(c->new CategoryView(c.id,c.name,c.kind)).toList(); }
    @Transactional(isolation=Isolation.READ_COMMITTED) public CategoryView category(UUID owner,NewCategory r) {
        writes.lock(owner);var c=new Category();c.id=UUID.randomUUID();c.userId=owner;c.name=r.name().strip();c.kind=r.kind();c.createdAt=Instant.now();em.persist(c);em.flush();return new CategoryView(c.id,c.name,c.kind);
    }
    private NewActual normalized(NewActual r) { return new NewActual(r.accountId(),r.categoryId(),r.direction(),amount(r.amount()),r.transactionDate(),r.note()==null ? "" : r.note(),r.occurrenceId(),r.plannedCashflowId()); }
    private ActualTransaction insert(UUID owner,NewActual r,UUID supersedes) {
        var account=writes.account(owner,r.accountId());writes.category(owner,r.categoryId(),r.direction());
        if(r.transactionDate().isBefore(account.openingDate) || r.transactionDate().isAfter(LocalDate.now())) throw invalid("Actual date must be between account opening and today.");
        if(r.occurrenceId()!=null && r.plannedCashflowId()!=null) throw invalid("Only one source link is allowed.");
        var a=new ActualTransaction();a.id=UUID.randomUUID();a.userId=owner;a.accountId=r.accountId();a.categoryId=r.categoryId();a.direction=r.direction();a.amount=amount(r.amount());a.transactionDate=r.transactionDate();a.note=r.note();a.createdAt=Instant.now();a.supersedesId=supersedes;em.persist(a);em.flush();
        if(r.occurrenceId()!=null || r.plannedCashflowId()!=null) planning.link(owner,a,r.occurrenceId(),r.plannedCashflowId());return a;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public String create(UUID owner,UUID key,NewActual request) {
        var r=normalized(request);return writes.once(owner,key,"create-actual",r,()->dto(insert(owner,r,null)));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public String correct(UUID owner,UUID id,UUID key,Correction request) {
        var r=normalized(request.transaction());String reason=reason(request.reason());
        return writes.once(owner,key,"correct-actual:"+id,new Correction(r,reason),()-> {
            var old=actual(owner,id);if(old.voidedAt!=null) throw conflict("Actual is already voided or corrected.");
            if(r.occurrenceId()!=null || r.plannedCashflowId()!=null) throw invalid("Corrections inherit the original source. Unlink first to change sources.");
            var link=planning.unlink(owner,id,reason);old.voidedAt=Instant.now();old.voidReason=reason;
            var replacement=insert(owner,r,id);
            if(link!=null) planning.link(owner,replacement,link.occurrenceId,link.plannedCashflowId);
            return dto(replacement);
        });
    }
    private String reason(String value) { if(value==null || value.isBlank() || value.length()>500) throw invalid("A reason of 1–500 characters is required.");return value.strip(); }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Actual voidActual(UUID owner,UUID id,String reason) {
        writes.lock(owner);var a=actual(owner,id);if(a.voidedAt==null) { a.voidedAt=Instant.now();a.voidReason=reason(reason);planning.unlink(owner,id,a.voidReason); }em.flush();return dto(a);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public String link(UUID owner,UUID id,UUID key,Source source) {
        return writes.once(owner,key,"link-actual:"+id,source,()-> { var a=actual(owner,id);planning.link(owner,a,source.occurrenceId(),source.plannedCashflowId());return dto(a); });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Actual unlink(UUID owner,UUID id,String reason) {
        writes.lock(owner);var a=actual(owner,id);planning.unlink(owner,id,reason(reason));return dto(a);
    }
    public Page list(UUID owner,int page,int size,UUID account,UUID category,String direction,LocalDate from,LocalDate to,String state) {
        if(page<0 || page>100000 || size<1 || size>100 || !(state.equals("ALL")||state.equals("VALID")||state.equals("VOIDED"))) throw invalid("Invalid pagination or state.");
        if(from!=null && to!=null && to.isBefore(from)) throw invalid("Invalid date range.");
        if(account!=null) writes.account(owner,account);if(category!=null) { var c=em.find(Category.class,category);if(c==null || !c.userId.equals(owner)) throw missing(); }
        if(direction!=null && !direction.equals("INCOME") && !direction.equals("EXPENSE")) throw invalid("Invalid direction.");
        var params=new HashMap<String,Object>();params.put("owner",owner);String where=" where userId=:owner";
        if(account!=null) { where+=" and accountId=:account";params.put("account",account); }
        if(category!=null) { where+=" and categoryId=:category";params.put("category",category); }
        if(direction!=null) { where+=" and direction=:direction";params.put("direction",direction); }
        if(from!=null) { where+=" and transactionDate>=:from";params.put("from",from); }
        if(to!=null) { where+=" and transactionDate<=:to";params.put("to",to); }
        if(state.equals("VALID")) where+=" and voidedAt is null";if(state.equals("VOIDED")) where+=" and voidedAt is not null";
        var query=em.createQuery("from ActualTransaction"+where+" order by transactionDate desc,createdAt desc,id",ActualTransaction.class);
        var count=em.createQuery("select count(a) from ActualTransaction a"+where,Long.class);
        params.forEach((key,value)-> { query.setParameter(key,value);count.setParameter(key,value); });
        return new Page(query.setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(this::dto).toList(),page,size,count.getSingleResult());
    }
    private BigDecimal sum(String entity,String dateField,UUID owner,UUID account,LocalDate from,LocalDate to,String direction) {
        String query="select coalesce(sum(amount),0) from "+entity+" where userId=:owner and voidedAt is null and "+dateField+" between :from and :to";
        if(account!=null) query+=" and accountId=:account";if(direction!=null) query+=" and direction=:direction";
        var q=em.createQuery(query,BigDecimal.class).setParameter("owner",owner).setParameter("from",from).setParameter("to",to);
        if(account!=null) q.setParameter("account",account);if(direction!=null) q.setParameter("direction",direction);
        return q.getSingleResult().setScale(2);
    }
    public Balance balance(UUID owner,UUID id,LocalDate asOf) {
        var a=writes.account(owner,id);if(asOf.isAfter(LocalDate.now())) throw invalid("As-of date cannot be in the future.");
        var zero=new BigDecimal("0.00");if(asOf.isBefore(a.openingDate)) return new Balance(id,asOf,false,zero,zero,zero,zero,zero);
        var income=sum("ActualTransaction","transactionDate",owner,id,a.openingDate,asOf,"INCOME");
        var expenses=sum("ActualTransaction","transactionDate",owner,id,a.openingDate,asOf,"EXPENSE");
        var payments=sum("InstallmentPayment","paidOn",owner,id,a.openingDate,asOf,null);
        return new Balance(id,asOf,true,a.openingBalance,income,expenses,payments,a.openingBalance.add(income).subtract(expenses).subtract(payments));
    }
    public Summary summary(UUID owner,UUID account,LocalDate from,LocalDate to) {
        if(to.isBefore(from) || to.isAfter(LocalDate.now())) throw invalid("Invalid summary range; actuals only through today.");
        if(account!=null) writes.account(owner,account);
        var income=sum("ActualTransaction","transactionDate",owner,account,from,to,"INCOME");
        var expenses=sum("ActualTransaction","transactionDate",owner,account,from,to,"EXPENSE");
        var payments=sum("InstallmentPayment","paidOn",owner,account,from,to,null);
        BigDecimal current=new BigDecimal("0.00");
        var accounts=account==null ? em.createQuery("from CashAccount where userId=:owner",CashAccount.class).setParameter("owner",owner).getResultList() : List.of(writes.account(owner,account));
        for(var a:accounts) current=current.add(balance(owner,a.id,to).currentBalance());
        return new Summary(from,to,income,expenses,payments,income.subtract(expenses).subtract(payments),current,
            "Manual records only; completeness and opening positions determine accuracy. Not a bank-confirmed balance. Planned events are excluded.");
    }
}
