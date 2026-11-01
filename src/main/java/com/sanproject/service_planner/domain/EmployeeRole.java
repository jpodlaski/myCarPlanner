package com.sanproject.service_planner.domain;

/**
 * Staff roles. Names travel as strings in JWT claims and localStorage, so they must match
 * mycarplanner-frontend/src/constants/roles.js.
 */
public enum EmployeeRole {
    ADMIN,
    CEO,
    COO,
    CLIENT_SERVICE_CONSULTANT,
    MECHANIC,
    APPRENTICE_MECHANIC;

    /** Management: may create, re-role and delete workers. */
    public boolean canManageWorkers() {
        return this == ADMIN || this == CEO || this == COO;
    }

    /** Roles that use the employee dashboard (consultant intake and workshop floor). */
    public boolean isWorkshopStaff() {
        return isConsultant() || isTechnician();
    }

    /** Accepts client requests and confirms appointments before work is claimed. */
    public boolean isConsultant() {
        return this == CLIENT_SERVICE_CONSULTANT;
    }

    /** Claims READY_FOR_WORK jobs and performs the service. */
    public boolean isTechnician() {
        return this == MECHANIC || this == APPRENTICE_MECHANIC;
    }

    /** ADMIN is reserved for back-office accounts, never for worker records. */
    public boolean isAssignableToWorker() {
        return this != ADMIN;
    }
}
