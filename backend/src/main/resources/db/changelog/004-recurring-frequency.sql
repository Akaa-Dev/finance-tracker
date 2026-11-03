ALTER TABLE recurring_rules DROP CONSTRAINT recurring_rules_frequency_check;
ALTER TABLE recurring_rules ADD CONSTRAINT recurring_rules_frequency_check
    CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'));
