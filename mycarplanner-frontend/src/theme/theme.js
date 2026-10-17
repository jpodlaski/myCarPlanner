/**
 * Theme resolution for the provider and the toggle.
 * THEME_STORAGE_KEY must match the bootstrap script in index.html.
 */
export const THEME_STORAGE_KEY = "mycarplanner_theme";

export const DARK = "dark";
export const LIGHT = "light";

const LIGHT_QUERY = "(prefers-color-scheme: light)";

export function isTheme(value) {
    return value === DARK || value === LIGHT;
}

export function getSystemTheme() {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
        return DARK;
    }
    return window.matchMedia(LIGHT_QUERY).matches ? LIGHT : DARK;
}

/** Explicit user choice, or null. */
export function getStoredTheme() {
    try {
        const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
        return isTheme(stored) ? stored : null;
    } catch {
        return null;
    }
}

export function storeTheme(theme) {
    try {
        window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
        // Private mode or storage disabled: the choice still applies for this visit.
    }
}

export function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
}

export function resolveInitialTheme() {
    return getStoredTheme() ?? getSystemTheme();
}

/** Subscribe to OS theme changes. Returns unsubscribe. */
export function watchSystemTheme(onChange) {
    const query = window.matchMedia(LIGHT_QUERY);
    const handler = (event) => onChange(event.matches ? LIGHT : DARK);
    query.addEventListener("change", handler);
    return () => query.removeEventListener("change", handler);
}
