package com.ledgerflow.common.security;

import com.ledgerflow.merchant.domain.Merchant;

public final class MerchantContext {

    private static final ThreadLocal<Merchant> CURRENT_MERCHANT = new ThreadLocal<>();

    private MerchantContext() {
    }

    public static void setMerchant(Merchant merchant) {
        CURRENT_MERCHANT.set(merchant);
    }

    public static Merchant getMerchant() {
        return CURRENT_MERCHANT.get();
    }

    public static void clear() {
        CURRENT_MERCHANT.remove();
    }
}
