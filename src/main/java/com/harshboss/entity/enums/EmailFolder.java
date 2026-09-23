package com.harshboss.entity.enums;

/**
 * Email folder (where the email client filed it).
 * Note: legitimate important emails are sometimes misfiled into SPAM — the AI
 * triage service scores true importance regardless of folder.
 */
public enum EmailFolder {
    INBOX,
    SPAM
}
