package com.financetracker.recurrence.application;
import com.financetracker.recurrence.api.PlanningContracts.*;
import com.financetracker.recurrence.domain.OccurrenceDates;
import com.financetracker.recurrence.persistence.*;
import com.financetracker.transaction.persistence.ActualTransaction;
import com.financetracker.common.FinancialWrites;
import static com.financetracker.common.FinancialWrites.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PlanningService {
    private final EntityManager em;private final FinancialWrites writes;
    public PlanningService(EntityManager em,FinancialWrites writes) { this.em=em;this.writes=writes; }
    private RecurringRule rule(UUID owner,UUID id) { var r=em.find(RecurringRule.class,id);if(r==null || !r.userId.equals(owner)) throw missing();return r; }
    private PlannedCashflow planned(UUID owner,UUID id) { var p=em.find(PlannedCashflow.class,id);if(p==null || !p.userId.equals(owner)) throw missing();return p; }
    private ActualLink active(String field,UUID id) {
        return em.createQuery("from ActualLink where "+field+"=:id and unlinkedAt is null",ActualLink.class).setParameter("id",id).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    private Rule dto(RecurringRule r) { return new Rule(r.id,r.name,r.accountId,r.categoryId,r.direction,r.amount,r.frequency,r.anchorDate,r.startDate,r.endDate,r.active); }
    private Planned dto(PlannedCashflow p) { var link=active("plannedCashflowId",p.id);return new Planned(p.id,p.name,p.accountId,p.categoryId,p.direction,p.amount,p.expectedDate,p.status,link==null ? null : link.transactionId); }
    private Occurrence dto(RecurrenceOccurrence o) { var r=rule(o.userId,o.ruleId);var link=active("occurrenceId",o.id);
        return new Occurrence(o.id,o.ruleId,r.name,o.occurrenceDate,o.plannedAmount,link==null ? null : link.transactionId,link==null ? "PENDING" : "REALIZED"); }
    public List<Rule> rules(UUID owner) { return em.createQuery("from RecurringRule where userId=:owner order by createdAt,id",RecurringRule.class).setParameter("owner",owner).getResultList().stream().map(this::dto).toList(); }
    public List<Planned> planned(UUID owner) { return em.createQuery("from PlannedCashflow where userId=:owner order by expectedDate,id",PlannedCashflow.class).setParameter("owner",owner).getResultList().stream().map(this::dto).toList(); }
    private void refs(UUID owner,UUID account,UUID category,String direction) { writes.category(owner,category,direction);if(account!=null) writes.account(owner,account); }
    @Transactional public String createRule(UUID owner,UUID key,NewRule request) {
        var r=new NewRule(request.name().strip(),request.accountId(),request.categoryId(),request.direction(),amount(request.amount()),request.frequency(),request.anchorDate(),request.startDate(),request.endDate());
        return writes.once(owner,key,"create-rule",r,()-> {
            refs(owner,r.accountId(),r.categoryId(),r.direction());
            if(r.endDate()!=null && r.endDate().isBefore(r.startDate())) throw invalid("End date must not precede start date.");
            var entity=new RecurringRule();entity.id=UUID.randomUUID();entity.userId=owner;entity.name=r.name();entity.accountId=r.accountId();entity.categoryId=r.categoryId();entity.direction=r.direction();entity.amount=r.amount();entity.frequency=r.frequency();entity.anchorDate=r.anchorDate();entity.startDate=r.startDate();entity.endDate=r.endDate();entity.active=true;entity.createdAt=Instant.now();entity.updatedAt=entity.createdAt;em.persist(entity);return dto(entity);
        });
    }
    @Transactional public Rule setActive(UUID owner,UUID id,boolean active) { writes.lock(owner);var r=rule(owner,id);r.active=active;r.updatedAt=Instant.now();return dto(r); }
    @Transactional public List<Occurrence> generate(UUID owner,UUID id,Range range) {
        writes.lock(owner);var r=rule(owner,id);if(!r.active) throw conflict("Activate the rule before generating occurrences.");
        List<LocalDate> dates;
        try { dates=OccurrenceDates.between(r.frequency,r.anchorDate,r.startDate,r.endDate,range.from(),range.to()); }
        catch(IllegalArgumentException e) { throw invalid(e.getMessage()); }
        for(var date:dates) {
            var found=em.createQuery("select count(o) from RecurrenceOccurrence o where ruleId=:id and occurrenceDate=:date",Long.class).setParameter("id",id).setParameter("date",date).getSingleResult();
            if(found==0) { var o=new RecurrenceOccurrence();o.id=UUID.randomUUID();o.userId=owner;o.ruleId=id;o.occurrenceDate=date;o.plannedAmount=r.amount;em.persist(o); }
        }
        em.flush();return occurrences(owner,id,range.from(),range.to());
    }
    public List<Occurrence> occurrences(UUID owner,UUID id,LocalDate from,LocalDate to) {
        var r=rule(owner,id);
        try { OccurrenceDates.between(r.frequency,r.anchorDate,r.startDate,r.endDate,from,to); } catch(IllegalArgumentException e) { throw invalid(e.getMessage()); }
        return em.createQuery("from RecurrenceOccurrence where ruleId=:id and occurrenceDate between :from and :to order by occurrenceDate",RecurrenceOccurrence.class)
            .setParameter("id",id).setParameter("from",from).setParameter("to",to).getResultList().stream().map(this::dto).toList();
    }
    @Transactional public String createPlanned(UUID owner,UUID key,NewPlanned request) {
        var r=new NewPlanned(request.name().strip(),request.accountId(),request.categoryId(),request.direction(),amount(request.amount()),request.expectedDate());
        return writes.once(owner,key,"create-planned",r,()-> {
            refs(owner,r.accountId(),r.categoryId(),r.direction());var p=new PlannedCashflow();p.id=UUID.randomUUID();p.userId=owner;p.name=r.name();p.accountId=r.accountId();p.categoryId=r.categoryId();p.direction=r.direction();p.amount=r.amount();p.expectedDate=r.expectedDate();p.status="PLANNED";p.createdAt=Instant.now();p.updatedAt=p.createdAt;em.persist(p);return dto(p);
        });
    }
    @Transactional public Planned status(UUID owner,UUID id,String status) {
        writes.lock(owner);if(!status.equals("PLANNED") && !status.equals("CANCELLED")) throw invalid("Only PLANNED or CANCELLED can be set explicitly.");var p=planned(owner,id);if(p.status.equals("REALIZED")) throw conflict("Unlink or void the actual before changing planned status.");
        p.status=status;p.updatedAt=Instant.now();return dto(p);
    }
    // Called only within a ledger write transaction, after locking the owner.
    public void link(UUID owner,ActualTransaction actual,UUID occurrenceId,UUID plannedId) {
        if((occurrenceId==null)==(plannedId==null)) throw invalid("Choose exactly one occurrence or planned event.");
        if(actual.voidedAt!=null) throw conflict("Cannot link a voided actual.");
        var old=active("transactionId",actual.id);
        if(old!=null) {
            if(Objects.equals(old.occurrenceId,occurrenceId) && Objects.equals(old.plannedCashflowId,plannedId)) return;
            throw conflict("Actual already has an active source link. Unlink it first.");
        }
        UUID category,account;String direction;
        if(occurrenceId!=null) {
            var o=em.find(RecurrenceOccurrence.class,occurrenceId);if(o==null || !o.userId.equals(owner)) throw missing();
            var r=rule(owner,o.ruleId);category=r.categoryId;account=r.accountId;direction=r.direction;
            if(active("occurrenceId",occurrenceId)!=null) throw conflict("Occurrence already realized.");
        } else {
            var p=planned(owner,plannedId);if(!p.status.equals("PLANNED") || active("plannedCashflowId",plannedId)!=null) throw conflict("Planned event is cancelled or already realized.");
            category=p.categoryId;account=p.accountId;direction=p.direction;p.status="REALIZED";p.updatedAt=Instant.now();
        }
        if(!actual.categoryId.equals(category) || !actual.direction.equals(direction) || (account!=null && !actual.accountId.equals(account)))
            throw invalid("Actual must match the source's category, direction and specified account.");
        var link=new ActualLink();link.id=UUID.randomUUID();link.userId=owner;link.transactionId=actual.id;link.occurrenceId=occurrenceId;link.plannedCashflowId=plannedId;link.linkedAt=Instant.now();em.persist(link);em.flush();
    }
    public ActualLink unlink(UUID owner,UUID transactionId,String reason) {
        var link=active("transactionId",transactionId);if(link==null) return null;if(!link.userId.equals(owner)) throw missing();
        link.unlinkedAt=Instant.now();link.unlinkReason=reason;
        if(link.plannedCashflowId!=null) { var p=planned(owner,link.plannedCashflowId);p.status="PLANNED";p.updatedAt=Instant.now(); }
        em.flush();return link;
    }
}
