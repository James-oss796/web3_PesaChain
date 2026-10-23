package com.pesachain.loans;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanServicingService {
    private final LoanRepository loans;
    public LoanServicingService(LoanRepository loans) { this.loans=loans; }

    @Transactional(readOnly=true)
    public LoanRepository.LoanView getMine(UserPrincipal borrower,UUID id) {
        requireBorrower(borrower);
        return loans.findMineById(borrower.id(),id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Loan was not found."));
    }

    @Transactional(readOnly=true)
    public LoanPage listMine(UserPrincipal borrower,int page,int size) {
        requireBorrower(borrower); validatePage(page,size);
        long offset=(long)page*size;
        if(offset>Integer.MAX_VALUE) throw invalid("Page is out of range.");
        return new LoanPage(loans.findMine(borrower.id(),size,(int)offset),page,size,loans.countMine(borrower.id()));
    }

    @Transactional(readOnly=true)
    public LoanPage listOfficerLoans(UserPrincipal officer,int page,int size) {
        requireOfficer(officer); validatePage(page,size);
        long offset=(long)page*size;
        if(offset>Integer.MAX_VALUE) throw invalid("Page is out of range.");
        return new LoanPage(loans.findAll(size,(int)offset),page,size,loans.countAll());
    }

    @Transactional
    public LoanRepository.LoanView disburse(UserPrincipal officer,UUID loanId,BigDecimal amount) {
        requireOfficer(officer);
        var loan=loans.lockById(loanId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Loan was not found."));
        if(!"APPROVED".equals(loan.status())) throw new ApiException(HttpStatus.CONFLICT,"INVALID_LOAN_STATE","Only an approved loan may be disbursed, and each loan may be disbursed once.");
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()-amount.scale()>28)
            throw invalid("Disbursement must be a positive KES amount with at most two decimal places.");
        BigDecimal normalized=amount.setScale(2,java.math.RoundingMode.UNNECESSARY);
        if(normalized.compareTo(loan.principal())!=0) throw invalid("Disbursement must equal the full approved principal.");
        if(!loans.disburse(loanId,officer.id(),normalized)) throw new ApiException(HttpStatus.CONFLICT,"INVALID_LOAN_STATE","The loan could not be disbursed in its current state.");
        return loans.findById(loanId).orElseThrow();
    }

    private static void requireBorrower(UserPrincipal user) {
        if(!"BORROWER".equals(user.role()) || !user.isEnabled()) throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Borrower access is required.");
    }
    private static void requireOfficer(UserPrincipal user) {
        if(!"LOAN_OFFICER".equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired()) throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Loan Officer access is required.");
    }
    private static void validatePage(int page,int size) { if(page<0 || size<1 || size>100) throw invalid("Page must be nonnegative and size must be between 1 and 100."); }
    private static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",detail); }
    public record LoanPage(List<LoanRepository.LoanView> items,int page,int size,long totalItems) {}
}
