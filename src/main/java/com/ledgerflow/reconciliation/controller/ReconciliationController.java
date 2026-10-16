package com.ledgerflow.reconciliation.controller;

import com.ledgerflow.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Financial invariant auditor and balance verification engine")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger on-demand financial reconciliation audit", description = "Audits all journal debits vs credits, wallet balances vs ledger history, and refund bounds.")
    public ResponseEntity<ReconciliationService.ReconciliationReport> triggerReconciliation(
            @RequestParam(defaultValue = "false") boolean simulateAnomaly) {

        ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation(simulateAnomaly);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/runs")
    @Operation(summary = "List reconciliation audit history")
    public ResponseEntity<List<ReconciliationService.ReconciliationReport>> listRuns() {
        List<ReconciliationService.ReconciliationReport> runs = reconciliationService.listRuns();
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get detailed results of a reconciliation run by ID")
    public ResponseEntity<ReconciliationService.ReconciliationReport> getRun(@PathVariable UUID id) {
        ReconciliationService.ReconciliationReport report = reconciliationService.getRun(id);
        return ResponseEntity.ok(report);
    }
}
