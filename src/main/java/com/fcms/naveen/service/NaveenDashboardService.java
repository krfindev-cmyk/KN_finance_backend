package com.fcms.naveen.service;

import com.fcms.naveen.dto.NaveenDashboard;
import com.fcms.naveen.model.NaveenLoanStatus;
import com.fcms.naveen.repository.NaveenBorrowingRepository;
import com.fcms.naveen.repository.NaveenLoanRepository;
import com.fcms.naveen.repository.NaveenSupplierRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class NaveenDashboardService {

    private final NaveenLoanService loanService;
    private final NaveenBorrowingService borrowingService;
    private final NaveenSupplierService supplierService;
    private final NaveenCashService cashService;
    private final NaveenLoanRepository loanRepository;
    private final NaveenBorrowingRepository borrowingRepository;
    private final NaveenSupplierRepository supplierRepository;

    public NaveenDashboardService(NaveenLoanService loanService, NaveenBorrowingService borrowingService,
                                   NaveenSupplierService supplierService, NaveenCashService cashService,
                                   NaveenLoanRepository loanRepository, NaveenBorrowingRepository borrowingRepository,
                                   NaveenSupplierRepository supplierRepository) {
        this.loanService = loanService;
        this.borrowingService = borrowingService;
        this.supplierService = supplierService;
        this.cashService = cashService;
        this.loanRepository = loanRepository;
        this.borrowingRepository = borrowingRepository;
        this.supplierRepository = supplierRepository;
    }

    public NaveenDashboard summary() {
        NaveenDashboard d = new NaveenDashboard();
        d.setMoneyToReceive(loanService.totalOutstandingReceivable());
        d.setMoneyToPay(borrowingService.totalOutstandingBalance());
        d.setSupplierBalance(supplierService.totalOutstandingBalance());
        d.setCashAvailable(cashService.summary(LocalDate.now()).getClosingBalance());
        d.setActiveLoans((int) loanRepository.findAll().stream().filter(l -> l.getStatus() == NaveenLoanStatus.Running).count());
        d.setActiveBorrowings(borrowingRepository.findAll().size());
        d.setActiveSuppliers(supplierRepository.findAll().size());

        double totalAmount = loanService.totalAmountAll() + borrowingService.totalPayableAll() + supplierService.totalPurchasesAll();
        double totalPaid = loanService.totalPaidAll() + borrowingService.totalRepaidAll() + supplierService.totalPaidAll();
        d.setTotalAmount(totalAmount);
        d.setTotalPaid(totalPaid);
        d.setTotalPending(Math.max(0, totalAmount - totalPaid));
        return d;
    }
}
