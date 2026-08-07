package com.beemdevelopment.aegis;


import java.util.concurrent.TimeUnit;

public enum PassReminderFreq {
    NEVER,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY;

    public long getDurationMillis() {
        long weeks;
        switch (this) {
            case WEEKLY:
                weeks = 1;
                break;
            case BIWEEKLY:
                weeks = 2;
                break;
            case MONTHLY:
                weeks = 4;
                break;
            case QUARTERLY:
                weeks = 13;
                break;
            default:
                weeks = 0;
                break;
        }

        return TimeUnit.MILLISECONDS.convert(weeks * 7L, TimeUnit.DAYS);
    }

    /** Returns the name of the string resource describing this frequency. */
    public String getStringRes() {
        switch (this) {
            case WEEKLY:
                return "password_reminder_freq_weekly";
            case BIWEEKLY:
                return "password_reminder_freq_biweekly";
            case MONTHLY:
                return "password_reminder_freq_monthly";
            case QUARTERLY:
                return "password_reminder_freq_quarterly";
            default:
                return "password_reminder_freq_never";
        }
    }

    public static PassReminderFreq fromInteger(int i) {
        return PassReminderFreq.values()[i];
    }
}
