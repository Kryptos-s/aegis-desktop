package com.beemdevelopment.aegis;

public enum GroupPlaceholderType {
    ALL,
    NEW_GROUP,
    NO_GROUP;

    /** Returns the name of the string resource for this placeholder. */
    public String getStringRes() {
        switch (this) {
            case ALL:
                return "all";
            case NEW_GROUP:
                return "new_group";
            case NO_GROUP:
                return "no_group";
            default:
                throw new IllegalArgumentException("Unexpected placeholder type: " + this);
        }
    }
}
