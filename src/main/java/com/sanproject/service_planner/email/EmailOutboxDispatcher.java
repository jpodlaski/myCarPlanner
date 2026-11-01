package com.sanproject.service_planner.email;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts delivery only after the transaction commits, so a rolled-back change never sends mail
 * and a crash mid-request still leaves a PENDING row for the poller.
 */
@Component
public class EmailOutboxDispatcher {

    private final EmailOutboxAsyncRunner asyncRunner;

    public EmailOutboxDispatcher(EmailOutboxAsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    public void dispatchAfterCommit(Long outboxId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncRunner.process(outboxId);
                }
            });
            return;
        }
        asyncRunner.process(outboxId);
    }
}
