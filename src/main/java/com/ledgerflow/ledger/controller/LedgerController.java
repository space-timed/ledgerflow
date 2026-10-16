package com.ledgerflow.ledger.controller;

import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.JournalLine;
import com.ledgerflow.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Double-entry journal exploration and audit history")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public record JournalLineResponse(
            UUID id,
            UUID accountId,
            String accountName,
            String accountType,
            String direction,
            long amountMinorUnits,
            String formattedAmount,
            String currency
    ) {}

    public record JournalEntryResponse(
            UUID id,
            String referenceId,
            String referenceType,
            String description,
            Instant postedAt,
            boolean isBalanced,
            long totalDebits,
            long totalCredits,
            List<JournalLineResponse> lines
    ) {}

    @GetMapping("/journals")
    @Operation(summary = "List recent double-entry journal entries")
    public ResponseEntity<List<JournalEntryResponse>> listJournals() {
        List<JournalEntry> entries = ledgerService.getRecentJournals();
        List<JournalEntryResponse> responses = entries.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/journals/{id}")
    @Operation(summary = "Inspect a double-entry journal entry and verified balanced postings")
    public ResponseEntity<JournalEntryResponse> getJournalEntry(@PathVariable UUID id) {
        JournalEntry entry = ledgerService.getJournalEntry(id);
        return ResponseEntity.ok(mapToResponse(entry));
    }

    private JournalEntryResponse mapToResponse(JournalEntry entry) {
        long debits = 0;
        long credits = 0;
        List<JournalLineResponse> lineResponses = new java.util.ArrayList<>();

        for (JournalLine line : entry.getLines()) {
            if (line.getDirection().name().equals("DEBIT")) {
                debits += line.getAmountMinorUnits();
            } else {
                credits += line.getAmountMinorUnits();
            }

            lineResponses.add(new JournalLineResponse(
                    line.getId(),
                    line.getAccount().getId(),
                    line.getAccount().getName(),
                    line.getAccount().getAccountType().name(),
                    line.getDirection().name(),
                    line.getAmountMinorUnits(),
                    line.getCurrency().formatMinorUnits(line.getAmountMinorUnits()),
                    line.getCurrency().name()
            ));
        }

        return new JournalEntryResponse(
                entry.getId(),
                entry.getReferenceId(),
                entry.getReferenceType().name(),
                entry.getDescription(),
                entry.getPostedAt(),
                (debits == credits),
                debits,
                credits,
                lineResponses
        );
    }
}
