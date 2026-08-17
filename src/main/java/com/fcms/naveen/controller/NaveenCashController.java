package com.fcms.naveen.controller;

import com.fcms.naveen.dto.NaveenCashSummary;
import com.fcms.naveen.model.NaveenCashEntry;
import com.fcms.naveen.service.NaveenCashService;
import com.fcms.service.AuthService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/naveen/cash")
public class NaveenCashController {

    private final NaveenCashService cashService;
    private final AuthService authService;

    public NaveenCashController(NaveenCashService cashService, AuthService authService) {
        this.cashService = cashService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping("/summary")
    public NaveenCashSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashService.summary(date);
    }

    @PostMapping("/entries")
    public NaveenCashEntry addEntry(@RequestBody NaveenCashEntry entry,
                                     @RequestParam(defaultValue = "system") String createdBy,
                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashService.addEntry(entry, createdBy);
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        cashService.deleteEntry(id);
    }

    @PutMapping("/entries/{id}")
    public NaveenCashEntry updateEntry(@PathVariable Long id, @RequestBody NaveenCashEntry entry,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashService.updateEntry(id, entry);
    }

    /** Plain list of every expense ever logged, for the Expenses page (no per-day ledger math). */
    @GetMapping("/entries")
    public java.util.List<NaveenCashEntry> listEntries(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashService.listAll();
    }
}
