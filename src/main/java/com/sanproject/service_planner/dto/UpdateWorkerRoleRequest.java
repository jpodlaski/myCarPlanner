package com.sanproject.service_planner.dto;

import com.sanproject.service_planner.domain.EmployeeRole;

import jakarta.validation.constraints.NotNull;

/** Worker role change; ADMIN cannot be assigned to worker accounts. */
public class UpdateWorkerRoleRequest {

    @NotNull(message = "Role is required")
    private EmployeeRole role;

    public EmployeeRole getRole() {
        return role;
    }

    public void setRole(EmployeeRole role) {
        this.role = role;
    }
}
