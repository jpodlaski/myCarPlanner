package com.sanproject.service_planner.dto;

import com.sanproject.service_planner.domain.Worker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/** Technician claims an unclaimed booking; the actor comes from the JWT. */
public class ClaimBookingRequest {

    @DecimalMin(value = "0.01", message = "Estimated cost must be positive")
    private BigDecimal estimatedCost;

    @NotEmpty(message = "At least one service type is required")
    private List<@NotBlank(message = "Service type cannot be blank") String> serviceTypes;

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public List<String> getServiceTypes() {
        return serviceTypes;
    }

    public void setServiceTypes(List<String> serviceTypes) {
        this.serviceTypes = serviceTypes;
    }
}
