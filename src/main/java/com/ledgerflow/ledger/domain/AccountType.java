package com.ledgerflow.ledger.domain;

public enum AccountType {
    ASSET,      // Positive when debited (e.g. Customer Wallets)
    LIABILITY,  // Positive when credited (e.g. Merchant Payable)
    EQUITY,     // Positive when credited (e.g. System Clearing / Capital)
    REVENUE,    // Positive when credited (e.g. Platform Fees)
    EXPENSE     // Positive when debited (e.g. Gateway / Interchange Fees)
}
