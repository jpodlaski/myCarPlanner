package com.sanproject.service_planner.domain;

/**
 * WORKSHOP: a consultant rejected or cancelled the booking before a technician claimed it.
 * WORKER: the assigned technician cancelled it.
 */
public enum CancelledBy {
    CUSTOMER,
    WORKER,
    WORKSHOP
}
