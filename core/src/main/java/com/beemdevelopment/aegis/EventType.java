package com.beemdevelopment.aegis;

public enum EventType {

    VAULT_UNLOCKED,
    VAULT_BACKUP_CREATED,
    VAULT_ANDROID_BACKUP_CREATED,
    VAULT_EXPORTED,
    ENTRY_SHARED,
    VAULT_UNLOCK_FAILED_PASSWORD,
    VAULT_UNLOCK_FAILED_BIOMETRICS;
    private static EventType[] _values;

    static {
        _values = values();
    }

    public static EventType fromInteger(int x) {
        return _values[x];
    }

    /** Returns the name of the string resource describing this event. */
    public static String getEventTitleRes(EventType eventType) {
        switch (eventType) {
            case VAULT_UNLOCKED:
                return "event_title_vault_unlocked";
            case VAULT_BACKUP_CREATED:
                return "event_title_backup_created";
            case VAULT_ANDROID_BACKUP_CREATED:
                return "event_title_android_backup_created";
            case VAULT_EXPORTED:
                return "event_title_vault_exported";
            case ENTRY_SHARED:
                return "event_title_entry_shared";
            case VAULT_UNLOCK_FAILED_PASSWORD:
                return "event_title_vault_unlock_failed_password";
            case VAULT_UNLOCK_FAILED_BIOMETRICS:
                return "event_title_vault_unlock_failed_biometrics";
            default:
                return "event_unknown";
        }
    }
}
