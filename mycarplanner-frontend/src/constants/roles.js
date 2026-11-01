/** Role names from the JWT "role" claim; must match EmployeeRole on the backend. */

/** Mirrors EmployeeRole.canManageWorkers(). */
export const MANAGEMENT_ROLES = ["ADMIN", "CEO", "COO"];

/** Roles that use the employee dashboard. */
export const WORKSHOP_ROLES = [
    "CLIENT_SERVICE_CONSULTANT",
    "MECHANIC",
    "APPRENTICE_MECHANIC",
];

/** Roles offered when creating a worker; ADMIN is back-office only. */
export const ASSIGNABLE_ROLES = [
    "CEO",
    "COO",
    "CLIENT_SERVICE_CONSULTANT",
    "MECHANIC",
    "APPRENTICE_MECHANIC",
];

export const ROLE_LABELS = {
    ADMIN: "Admin",
    CEO: "CEO",
    COO: "COO",
    CLIENT_SERVICE_CONSULTANT: "Client service consultant",
    MECHANIC: "Mechanic",
    APPRENTICE_MECHANIC: "Apprentice mechanic",
};

export function roleLabel(role) {
    return ROLE_LABELS[role] ?? role;
}

export function isConsultant(role) {
    return role === "CLIENT_SERVICE_CONSULTANT";
}

export function isTechnician(role) {
    return role === "MECHANIC" || role === "APPRENTICE_MECHANIC";
}
