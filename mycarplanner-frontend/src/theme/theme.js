/**
 * Theme resolution for the provider and the toggle.
 * THEME_STORAGE_KEY must match the bootstrap script in index.html.
 */
export const THEME_STORAGE_KEY = "mycarplanner_theme";

export const DARK = "dark";
export const LIGHT = "light";

/** Used when the visitor has not chosen a theme. */
export const DEFAULT_THEME = LIGHT;

export function isTheme(value) {
    return value === DARK || value === LIGHT;
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

// Browser chrome colour; matches --bg in src/index.css.
const THEME_COLORS = { [LIGHT]: "#ffffff", [DARK]: "#000000" };

export function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", THEME_COLORS[theme]);
}

export function resolveInitialTheme() {
    return getStoredTheme() ?? DEFAULT_THEME;
}
