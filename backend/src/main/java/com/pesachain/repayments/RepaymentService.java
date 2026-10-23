package com.pesachain.repayments;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepaymentService {
    private final RepaymentRepository repository;
    private final String signingSecret;

    public RepaymentService(RepaymentRepository repository,
            @Value("${pesachain.simulation.hmac-secret:}") String signingSecret) {
        this.repository = repository;
        this.signingSecret = signingSecret;
    }

    @Transactional
    public RepaymentRepository.Event initiate(UserPrincipal borrower, UUID loanId, BigDecimal amount, UUID idempotencyKey) {
        requireBorrower(borrower);
        validateAmount(amount);
        requireConfiguredSecret();
        var previous=repository.findByIdempotencyKey(borrower.id(),idempotencyKey);
        if (previous.isPresent()) {
            var event=previous.get();
            if (!event.loanId().equals(loanId) || event.amount().compareTo(amount)!=0)
                throw conflict("DUPLICATE_OPERATION","This idempotency key was already used for a different simulation.");
            return event;
        }
        var loan=repository.findLoan(loanId).filter(l->l.borrowerId().equals(borrower.id()))
                .orElseThrow(()->notFound("Loan was not found."));
        if (!"ACTIVE".equals(loan.status())) throw conflict("INVALID_LOAN_STATE","Only an active loan can receive a simulated repayment.");
        if (amount.compareTo(loan.outstandingBalance())>0) throw invalid("Simulation amount exceeds the current outstanding balance.");
        UUID eventId=UUID.randomUUID();
        Instant eventTime=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String signature=sign(canonical(eventId,loanId,borrower.id(),amount,eventTime));
        var created=repository.createEvent(eventId,borrower.id(),loanId,amount.setScale(2),idempotencyKey,signature,eventTime);
        var event=created.orElseThrow(()->conflict("DUPLICATE_OPERATION","This simulation request has already been processed."));
        if (!event.id().equals(eventId) && (!event.loanId().equals(loanId) || event.amount().compareTo(amount)!=0))
            throw conflict("DUPLICATE_OPERATION","This idempotency key was already used for a different simulation.");
        if (created.isPresent() && event.id().equals(eventId)) {
            repository.auditEvent(borrower.id(),borrower.role(),"SIMULATED_REPAYMENT_INITIATED",eventId);
        }
        return event;
    }

    @Transactional(readOnly=true)
    public List<EventResponse> borrowerEvents(UserPrincipal borrower,UUID loanId) {
        requireBorrower(borrower);
        repository.findLoan(loanId).filter(l->l.borrowerId().equals(borrower.id())).orElseThrow(()->notFound("Loan was not found."));
        return repository.listBorrowerEvents(borrower.id(),loanId).stream().map(EventResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public List<RepaymentRepository.Repayment> borrowerRepayments(UserPrincipal borrower,UUID loanId) {
        requireBorrower(borrower);
        repository.findLoan(loanId).filter(l->l.borrowerId().equals(borrower.id())).orElseThrow(()->notFound("Loan was not found."));
        return repository.listBorrowerRepayments(borrower.id(),loanId);
    }
    @Transactional(readOnly=true)
    public List<EventResponse> officerQueue(UserPrincipal officer,int page,int size,String status) {
        requireOfficer(officer); validatePage(page,size);
        if (!List.of("PENDING","VERIFIED").contains(status)) throw invalid("Status must be PENDING or VERIFIED.");
        long offset=(long)page*size; if(offset>Integer.MAX_VALUE) throw invalid("Page is out of range.");
        return repository.listOfficerEvents(status,size,(int)offset).stream().map(EventResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public EventResponse officerEvent(UserPrincipal officer,UUID eventId) {
        requireOfficer(officer);
        return repository.findEvent(eventId).map(EventResponse::from).orElseThrow(()->notFound("Repayment event was not found."));
    }
    @Transactional
    public EventResponse verify(UserPrincipal officer,UUID eventId) {
        requireOfficer(officer); requireConfiguredSecret();
        var event=repository.lockEvent(eventId).orElseThrow(()->notFound("Repayment event was not found."));
        if (!"PENDING".equals(event.status())) throw conflict("INVALID_STATE_TRANSITION","Only a pending simulation can be verified.");
        var loan=repository.findLoan(event.loanId()).orElseThrow(()->notFound("Loan was not found."));
        if (!"ACTIVE".equals(loan.status())) throw conflict("INVALID_LOAN_STATE","Repayments may only be verified for an active loan.");
        verifySignature(event);
        if (!repository.verify(eventId,officer.id())) throw conflict("INVALID_STATE_TRANSITION","The simulation was already processed.");
        repository.auditEvent(officer.id(),officer.role(),"SIMULATED_REPAYMENT_VERIFIED",eventId);
        return repository.findEvent(eventId).map(EventResponse::from).orElseThrow();
    }
    @Transactional
    public RepaymentRepository.Repayment record(UserPrincipal officer,UUID eventId) {
        requireOfficer(officer); requireConfiguredSecret();
        var event=repository.lockEvent(eventId).orElseThrow(()->notFound("Repayment event was not found."));
        if (!"VERIFIED".equals(event.status())) throw conflict("INVALID_STATE_TRANSITION","Only a verified simulation may be recorded, and it may be recorded once.");
        verifySignature(event);
        var loan=repository.lockLoan(event.loanId()).orElseThrow(()->notFound("Loan was not found."));
        if (!"ACTIVE".equals(loan.status())) throw conflict("INVALID_LOAN_STATE","Only an active loan may receive a repayment.");
        if (event.amount().compareTo(loan.outstandingBalance())>0) throw conflict("OVERPAYMENT_REJECTED","Repayment exceeds the current outstanding balance.");
        BigDecimal resulting=loan.outstandingBalance().subtract(event.amount()).setScale(2,java.math.RoundingMode.UNNECESSARY);
        return repository.record(eventId,officer.id(),event.amount(),resulting,resulting.signum()==0);
    }

    public record EventResponse(UUID id,UUID loanId,BigDecimal amount,String status,Instant createdAt,
            Instant sourceVerifiedAt,Instant verifiedAt,Instant recordedAt) {
        static EventResponse from(RepaymentRepository.Event event) {
            return new EventResponse(event.id(),event.loanId(),event.amount(),event.status(),event.createdAt(),
                    event.sourceVerifiedAt(),event.verifiedAt(),event.recordedAt());
        }
    }

    private void verifySignature(RepaymentRepository.Event event) {
        String expected=sign(canonical(event.id(),event.loanId(),event.borrowerId(),event.amount(),event.createdAt()));
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected),HexFormat.of().parseHex(event.signature())))
            throw conflict("SIMULATION_VERIFICATION_FAILED","The simulated event signature could not be verified.");
    }
    private String sign(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("Simulation signer is unavailable.",failure); }
    }
    private static String canonical(UUID event,UUID loan,UUID borrower,BigDecimal amount,Instant at) {
        return event+"|"+loan+"|"+borrower+"|"+amount.setScale(2,java.math.RoundingMode.UNNECESSARY).toPlainString()+"|"+at;
    }
    private void requireConfiguredSecret() {
        if (signingSecret==null || signingSecret.getBytes(StandardCharsets.UTF_8).length<32)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SIMULATION_VERIFICATION_UNAVAILABLE","Simulated repayment verification is not configured.");
    }
    private static void validateAmount(BigDecimal amount) {
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()-amount.scale()>28)
            throw invalid("Repayment must be a positive KES amount with at most two decimal places.");
    }
    private static void requireBorrower(UserPrincipal user) {
        if(!"BORROWER".equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired()) throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Borrower access is required.");
    }
    private static void requireOfficer(UserPrincipal user) {
        if(!"LOAN_OFFICER".equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired()) throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Loan Officer access is required.");
    }
    private static void validatePage(int page,int size) { if(page<0 || size<1 || size>100) throw invalid("Page must be nonnegative and size must be between 1 and 100."); }
    private static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",detail); }
    private static ApiException notFound(String detail) { return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND",detail); }
    private static ApiException conflict(String code,String detail) { return new ApiException(HttpStatus.CONFLICT,code,detail); }
}
