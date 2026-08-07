package com.beemdevelopment.aegis;

import com.beemdevelopment.aegis.helpers.comparators.LastUsedComparator;
import com.beemdevelopment.aegis.helpers.comparators.UsageCountComparator;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.helpers.comparators.AccountNameComparator;
import com.beemdevelopment.aegis.helpers.comparators.IssuerNameComparator;

import java.util.Collections;
import java.util.Comparator;

public enum SortCategory {
    CUSTOM,
    ACCOUNT,
    ACCOUNT_REVERSED,
    ISSUER,
    ISSUER_REVERSED,
    USAGE_COUNT,
    LAST_USED;

    private static SortCategory[] _values;

    static {
        _values = values();
    }

    public static SortCategory fromInteger(int x) {
        return _values[x];
    }

    public Comparator<VaultEntry> getComparator() {
        Comparator<VaultEntry> comparator = null;

        switch (this) {
            case ACCOUNT:
                comparator = new AccountNameComparator().thenComparing(new IssuerNameComparator());
                break;
            case ACCOUNT_REVERSED:
                comparator = Collections.reverseOrder(new AccountNameComparator().thenComparing(new IssuerNameComparator()));
                break;
            case ISSUER:
                comparator = new IssuerNameComparator().thenComparing(new AccountNameComparator());
                break;
            case ISSUER_REVERSED:
                comparator = Collections.reverseOrder(new IssuerNameComparator().thenComparing(new AccountNameComparator()));
                break;
            case USAGE_COUNT:
                comparator = Collections.reverseOrder(new UsageCountComparator());
                break;
            case LAST_USED:
                comparator = Collections.reverseOrder(new LastUsedComparator());
        }

        return comparator;
    }

}
