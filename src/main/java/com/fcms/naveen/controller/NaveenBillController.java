package com.fcms.naveen.controller;

import com.fcms.naveen.dto.BillDetail;
import com.fcms.naveen.dto.BillRequest;
import com.fcms.naveen.service.NaveenBillPdfService;
import com.fcms.naveen.service.NaveenBillService;
import com.fcms.service.AuthService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/naveen/bills")
public class NaveenBillController {

    private final NaveenBillService billService;
    private final NaveenBillPdfService billPdfService;
    private final AuthService authService;

    public NaveenBillController(NaveenBillService billService, NaveenBillPdfService billPdfService, AuthService authService) {
        this.billService = billService;
        this.billPdfService = billPdfService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping
    public List<BillDetail> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return billService.listAll();
    }

    @GetMapping("/{id}")
    public BillDetail getOne(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return billService.getOne(id);
    }

    @PostMapping
    public BillDetail create(@RequestBody BillRequest req, @RequestParam(defaultValue = "system") String createdBy,
                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return billService.createBill(req, createdBy);
    }

    @PutMapping("/{id}")
    public BillDetail update(@PathVariable Long id, @RequestBody BillRequest req, @RequestParam(defaultValue = "system") String editedBy,
                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return billService.updateBill(id, req, editedBy);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        billService.deleteBill(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        BillDetail detail = billService.getOne(id);
        byte[] data = billPdfService.generate(detail);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("bill-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
