package com.pesachain.applications;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanDecisionService {
    private static final BigDecimal ONE_HUNDRED=BigDecimal.valueOf(100);
    private final ApplicationRepository applications;
    public LoanDecisionService(ApplicationRepository applications) { this.applications=applications; }

    @Transactional(readOnly=true)
    public OfficerApplicationPage listPending(UserPrincipal officer,int page,int size,String borrowerNameQuery) {
        requireOfficer(officer);
        validatePage(page,size);
        String prefix=normalizeSearch(borrowerNameQuery);
        long offset=(long)page*size;
        if(offset>Integer.MAX_VALUE) throw invalid("Page is out of range.");
        List<OfficerApplication> items=applications.findPendingForOfficer(size,(int)offset,prefix).stream().map(OfficerApplication::from).toList();
        return new OfficerApplicationPage(items,page,size,applications.countPendingForOfficer(prefix));
    }

    @Transactional(readOnly=true)
    public OfficerApplication get(UserPrincipal officer,UUID id) {
        requireOfficer(officer);
        return applications.findForOfficer(id).map(OfficerApplication::from)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Application was not found."));
    }

    @Transactional
    public OfficerApplication decide(UserPrincipal officer,UUID id,DecisionCommand command) {
        requireOfficer(officer);
        if(command==null || !("APPROVE".equals(command.decision()) || "REJECT".equals(command.decision())))
            throw invalid("Decision must be APPROVE or REJECT.");
        String rationale=trimmed(command.rationale());
        if(rationale==null || rationale.length()>2000) throw invalid("Provide a decision rationale of up to 2000 characters.");
        var existing=applications.findForOfficer(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Application was not found."));
        if(!"PENDING".equals(existing.application().status()))
            throw new ApiException(HttpStatus.CONFLICT,"APPLICATION_ALREADY_DECIDED","This application already has a decision.");

        boolean approved="APPROVE".equals(command.decision());
        BigDecimal principal=null,rate=null,interest=null,total=null;
        Integer term=null;
        String rejection=null;
        if(approved) {
            principal=normalizeMoney(command.approvedPrincipal(),"Approved principal");
            term=command.approvedTermMonths();
            rate=command.flatRatePercent();
            if(term==null || term<1) throw invalid("Approved term must be a positive number of months.");
            if(rate==null || rate.signum()<0 || rate.scale()>10 || rate.precision()-rate.scale()>20) throw invalid("Flat interest rate must be zero or greater and within supported numeric precision.");
            if(existing.application().requestedTermMonths()==null) throw new ApiException(HttpStatus.CONFLICT,"REQUESTED_TERM_MISSING","This legacy application has no requested term and cannot be approved.");
            interest=principal.multiply(rate).divide(ONE_HUNDRED,2,RoundingMode.HALF_UP);
            total=principal.add(interest).setScale(2,RoundingMode.HALF_UP);
            if(command.rejectionReason()!=null && !command.rejectionReason().isBlank()) throw invalid("Do not provide a rejection reason when approving.");
        } else {
            rejection=trimmed(command.rejectionReason());
            if(rejection==null || rejection.length()>1000) throw invalid("Provide a borrower-readable rejection reason of up to 1000 characters.");
            if(command.approvedPrincipal()!=null || command.approvedTermMonths()!=null || command.flatRatePercent()!=null)
                throw invalid("Approved financial terms can only be provided when approving.");
        }
        boolean saved=applications.recordDecision(id,officer.id(),approved,rationale,rejection,principal,
                term==null?0:term,rate,interest,total);
        if(!saved) throw new ApiException(HttpStatus.CONFLICT,"APPLICATION_ALREADY_DECIDED","This application already has a decision.");
        return get(officer,id);
    }

    private static BigDecimal normalizeMoney(BigDecimal amount,String label) {
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()-amount.scale()>28)
            throw invalid(label+" must be positive KES with at most two decimal places.");
        try { return amount.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException exception) { throw invalid(label+" must be positive KES with at most two decimal places."); }
    }
    private static String trimmed(String value) { return value==null?null:value.trim(); }
    private static String normalizeSearch(String query) {
        if(query==null || query.isBlank()) return null;
        String value=query.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[%_\\\\]", "");
        if(value.length()<2 || value.length()>80) throw invalid("Borrower search must contain between 2 and 80 characters.");
        return value;
    }
    private static void requireOfficer(UserPrincipal user) {
        if(!"LOAN_OFFICER".equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired())
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Loan Officer access is required.");
    }
    private static void validatePage(int page,int size) { if(page<0 || size<1 || size>100) throw invalid("Page must be nonnegative and size must be between 1 and 100."); }
    private static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",detail); }

    public record DecisionCommand(String decision,BigDecimal approvedPrincipal,Integer approvedTermMonths,
                                  BigDecimal flatRatePercent,String rationale,String rejectionReason) {}
    public record OfficerApplication(UUID id,UUID borrowerId,String borrowerName,BigDecimal requestedAmount,
            Integer requestedTermMonths,String purpose,String status,java.time.Instant submittedAt,
            String decisionRationale,java.time.Instant decisionAt,BigDecimal approvedPrincipal,
            Integer approvedTermMonths,BigDecimal approvedFlatRatePercent,BigDecimal calculatedInterest,
            BigDecimal totalRepayable,String rejectionReason) {
        static OfficerApplication from(ApplicationRepository.OfficerApplicationView view) {
            var a=view.application();
            return new OfficerApplication(a.id(),view.borrowerId(),view.borrowerName(),a.requestedAmount(),
                    a.requestedTermMonths(),a.purpose(),a.status(),a.submittedAt(),view.decisionRationale(),
                    a.decisionAt(),a.approvedPrincipal(),a.approvedTermMonths(),a.approvedFlatRatePercent(),
                    a.calculatedInterest(),a.totalRepayable(),a.rejectionReason());
        }
    }
    public record OfficerApplicationPage(List<OfficerApplication> items,int page,int size,long totalItems) {}
}
