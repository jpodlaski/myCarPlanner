package com.sanproject.service_planner.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Appointment the consultant confirms when accepting a request; the actor comes from the JWT. */
public class ScheduleBookingRequest {

    @NotNull(message = "Scheduled date and time is required")
    private LocalDateTime scheduledDateTime;

    public LocalDateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
}
