package com.fcms.seed;

import com.fcms.model.*;
import com.fcms.repository.AppUserRepository;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import com.fcms.service.CustomerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final AppUserRepository appUserRepository;
    private final CustomerService customerService;

    public DataSeeder(CustomerRepository customerRepository, PaymentRepository paymentRepository,
                       AppUserRepository appUserRepository, CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.appUserRepository = appUserRepository;
        this.customerService = customerService;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        if (customerRepository.count() == 0) {
            seedCustomers();
        }
    }

    private void seedUsers() {
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser();
            admin.setName("Admin User");
            admin.setUsername("admin");
            admin.setPassword("admin123");
            admin.setRole(Role.Admin);
            appUserRepository.save(admin);
        }
        if (appUserRepository.findByUsername("staff").isEmpty()) {
            AppUser staff = new AppUser();
            staff.setName("Staff User");
            staff.setUsername("staff");
            staff.setPassword("staff123");
            staff.setRole(Role.Staff);
            appUserRepository.save(staff);
        }
    }

    private void seedCustomers() {
        String[] names = {
                "Ramesh Kumar", "Suresh Babu", "Lakshmi Devi", "Priya Sharma", "Anitha Raj",
                "Karthik Raja", "Vijay Kumar", "Meena Kumari", "Sathish Kumar", "Divya Bharathi"
        };
        String[] addresses = {
                "12 Gandhi Street, Chennai", "45 Nehru Road, Coimbatore", "8 Anna Nagar, Madurai",
                "23 MG Road, Salem", "56 Kamaraj Street, Trichy", "34 Bazaar Street, Erode",
                "9 Market Road, Vellore", "67 Temple Street, Tirunelveli", "18 Station Road, Tiruppur",
                "29 Main Road, Thanjavur"
        };
        Random random = new Random(42);
        String collectedBy = "Admin User";

        for (int i = 0; i < names.length; i++) {
            Customer c = new Customer();
            c.setName(names[i]);
            c.setMobile("98" + String.format("%08d", 10000000 + i * 111111 % 89999999));
            c.setAlternateMobile("90" + String.format("%08d", 20000000 + i * 222222 % 79999999));
            c.setAddress(addresses[i]);

            boolean weekly = i % 3 == 0;
            FinanceType type = weekly ? FinanceType.Weekly : FinanceType.Daily;
            c.setFinanceType(type);

            double financeAmount = 20000 + (i * 5000);
            c.setFinanceAmount(financeAmount);
            c.setInterest(10.0 + (i % 3) * 2);

            LocalDate start = LocalDate.now().minusDays(30 + i * 5);
            c.setStartDate(start);

            if (weekly) {
                c.setCollectionDay(new String[]{"Monday", "Wednesday", "Friday"}[i % 3]);
                c.setTotalInstallments(20);
                c.setInstallmentAmount(Math.round((financeAmount * 1.15 / 20.0) * 100.0) / 100.0);
            } else {
                c.setCollectionDay(null);
                c.setTotalInstallments(100);
                c.setInstallmentAmount(Math.round((financeAmount * 1.15 / 100.0) * 100.0) / 100.0);
            }

            int paidCount = 5 + (i * 3) % 15;
            c.setPaidInstallments(paidCount);
            double totalPaid = paidCount * c.getInstallmentAmount();
            c.setTotalPaid(Math.round(totalPaid * 100.0) / 100.0);

            c.setStatus(i == 9 ? CustomerStatus.Closed : (paidCount >= 18 ? CustomerStatus.Completed : CustomerStatus.Running));
            c.setCreatedAt(LocalDateTime.now().minusDays(30 + i * 5));

            LocalDate lastPaymentDate = weekly ? start.plusWeeks(paidCount) : start.plusDays(paidCount);
            c.setLastPaymentDate(lastPaymentDate);
            c.setLastPaymentAmount(c.getInstallmentAmount());
            c.setNextDueDate(customerService.computeNextDueDate(lastPaymentDate, type));

            customerService.recomputeDerived(c);
            Customer saved = customerRepository.save(c);

            // seed a few payments per customer
            int paymentsToSeed = Math.min(paidCount, 6);
            for (int p = 0; p < paymentsToSeed; p++) {
                Payment payment = new Payment();
                payment.setCustomerId(saved.getId());
                LocalDate pd = weekly ? start.plusWeeks(p) : start.plusDays(p);
                payment.setDate(pd);
                payment.setAmount(saved.getInstallmentAmount());
                payment.setType(PaymentType.Paid);
                payment.setCollectedBy(collectedBy);
                payment.setNotes("Regular collection");
                payment.setCreatedAt(pd.atStartOfDay());
                paymentRepository.save(payment);
            }
        }
    }
}
