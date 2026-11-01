import { MANAGEMENT_ROLES, WORKSHOP_ROLES } from "../constants/roles";

/**
 * Session stored in localStorage as { token, role, id, name }. Clearing it is broadcast through
 * the storage event and BroadcastChannel so other open tabs sign out too.
 */
const SESSION_KEY = "mycarplanner_session";
const SESSION_CHANNEL = "mycarplanner-session";

function broadcastSessionCleared() {
    try {
        const channel = new BroadcastChannel(SESSION_CHANNEL);
        channel.postMessage({ type: "session-cleared" });
        channel.close();
    } catch {
        // BroadcastChannel unsupported — storage event still covers other tabs.
    }
}

export function saveSession(session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function getSession() {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    try {
        const session = JSON.parse(raw);
        // Sessions from before JWT auth have no token and cannot call the API.
        if (!session?.token) return null;
        return session;
    } catch {
        return null;
    }
}

export function clearSession() {
    localStorage.removeItem(SESSION_KEY);
    broadcastSessionCleared();
}

/** Subscribe to logout in other tabs (account deletion confirm, Sign out, etc.). */
export function subscribeSessionCleared(onCleared) {
    const onStorage = (event) => {
        if (event.key === SESSION_KEY && event.newValue === null) {
            onCleared();
        }
    };

    window.addEventListener("storage", onStorage);

    let channel;
    try {
        channel = new BroadcastChannel(SESSION_CHANNEL);
        channel.onmessage = (event) => {
            if (event?.data?.type === "session-cleared") {
                onCleared();
            }
        };
    } catch {
        channel = null;
    }

    return () => {
        window.removeEventListener("storage", onStorage);
        channel?.close();
    };
}

export function isClient() {
    return getSession()?.role === "CLIENT";
}

export function canManageWorkers() {
    return MANAGEMENT_ROLES.includes(getSession()?.role);
}

// Maps API role string to the default dashboard route after login.
export function getDashboardPathForRole(role) {
    if (role === "CLIENT") return "/client";
    if (MANAGEMENT_ROLES.includes(role)) return "/admin";
    if (WORKSHOP_ROLES.includes(role)) return "/employee";
    return "/employee-login"; // unknown roles fall back to staff login
}
