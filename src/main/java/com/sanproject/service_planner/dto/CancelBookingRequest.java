package com.sanproject.service_planner.dto;

/** Cancellation reason (always required); the actor comes from the JWT. */
public class CancelBookingRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
