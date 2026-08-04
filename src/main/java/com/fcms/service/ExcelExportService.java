package com.fcms.service;

import com.fcms.model.Customer;
import com.fcms.model.FinanceType;
import com.fcms.model.CustomerStatus;
import com.fcms.model.Payment;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExcelExportService {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;

    public ExcelExportService(CustomerRepository customerRepository, PaymentRepository paymentRepository,
                               CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.customerService = customerService;
    }

    public byte[] exportCustomers(String q, CustomerStatus status, FinanceType financeType,
                                   String paymentStatus, Boolean overdue) {
        List<Customer> customers = customerService.search(q, status, financeType, paymentStatus, overdue);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Customers");
            CellStyle headerStyle = boldStyle(workbook);

            String[] headers = {"ID", "Name", "Mobile", "Alternate Mobile", "Address", "Finance Amount", "Interest",
                    "Start Date", "Finance Type", "Collection Day", "Installment Amount", "Total Installments",
                    "Paid Installments", "Total Amount", "Total Paid", "Pending Amount", "Current Balance",
                    "Next Due Date", "Last Payment Date", "Last Payment Amount", "Status", "Created At"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Customer c : customers) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(c.getId() == null ? 0 : c.getId());
                row.createCell(col++).setCellValue(nz(c.getName()));
                row.createCell(col++).setCellValue(nz(c.getMobile()));
                row.createCell(col++).setCellValue(nz(c.getAlternateMobile()));
                row.createCell(col++).setCellValue(nz(c.getAddress()));
                row.createCell(col++).setCellValue(nz(c.getFinanceAmount()));
                row.createCell(col++).setCellValue(nz(c.getInterest()));
                row.createCell(col++).setCellValue(nz(c.getStartDate()));
                row.createCell(col++).setCellValue(nz(c.getFinanceType()));
                row.createCell(col++).setCellValue(nz(c.getCollectionDay()));
                row.createCell(col++).setCellValue(nz(c.getInstallmentAmount()));
                row.createCell(col++).setCellValue(c.getTotalInstallments() == null ? 0 : c.getTotalInstallments());
                row.createCell(col++).setCellValue(c.getPaidInstallments() == null ? 0 : c.getPaidInstallments());
                row.createCell(col++).setCellValue(nz(c.getTotalAmount()));
                row.createCell(col++).setCellValue(nz(c.getTotalPaid()));
                row.createCell(col++).setCellValue(nz(c.getPendingAmount()));
                row.createCell(col++).setCellValue(nz(c.getCurrentBalance()));
                row.createCell(col++).setCellValue(nz(c.getNextDueDate()));
                row.createCell(col++).setCellValue(nz(c.getLastPaymentDate()));
                row.createCell(col++).setCellValue(nz(c.getLastPaymentAmount()));
                row.createCell(col++).setCellValue(nz(c.getStatus()));
                row.createCell(col++).setCellValue(c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate customers Excel export", e);
        }
    }

    public byte[] exportPayments(LocalDate from, LocalDate to) {
        List<Payment> payments = paymentRepository.findAll();
        if (from != null && to != null) {
            payments = payments.stream()
                    .filter(p -> p.getDate() != null && !p.getDate().isBefore(from) && !p.getDate().isAfter(to))
                    .toList();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payments");
            CellStyle headerStyle = boldStyle(workbook);

            String[] headers = {"ID", "Customer", "Date", "Amount", "Type", "Collected By", "Notes",
                    "Edited", "Edited At", "Edited By", "Edit Reason"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Payment p : payments) {
                String customerName = p.getCustomerId() == null ? "" :
                        customerRepository.findById(p.getCustomerId()).map(Customer::getName).orElse("#" + p.getCustomerId());
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(p.getId() == null ? 0 : p.getId());
                row.createCell(col++).setCellValue(nz(customerName));
                row.createCell(col++).setCellValue(nz(p.getDate()));
                row.createCell(col++).setCellValue(nz(p.getAmount()));
                row.createCell(col++).setCellValue(nz(p.getType()));
                row.createCell(col++).setCellValue(nz(p.getCollectedBy()));
                row.createCell(col++).setCellValue(nz(p.getNotes()));
                row.createCell(col++).setCellValue(p.isEdited());
                row.createCell(col++).setCellValue(p.getEditedAt() == null ? "" : p.getEditedAt().toString());
                row.createCell(col++).setCellValue(nz(p.getEditedBy()));
                row.createCell(col++).setCellValue(nz(p.getEditReason()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate payments Excel export", e);
        }
    }

    private CellStyle boldStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private String nz(String s) { return s == null ? "" : s; }
    private String nz(Object o) { return o == null ? "" : String.valueOf(o); }
    private double nz(Double d) { return d == null ? 0.0 : d; }
}
