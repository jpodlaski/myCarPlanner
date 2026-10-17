import { DARK } from "../theme/theme";
import { useTheme } from "../theme/useTheme";
import "./ThemeToggle.css";

function SunIcon() {
    return (
        <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            aria-hidden="true"
            focusable="false"
        >
            <circle cx="12" cy="12" r="4.5" />
            <path d="M12 1.5v2.6M12 19.9v2.6M4.2 4.2l1.9 1.9M17.9 17.9l1.9 1.9M1.5 12h2.6M19.9 12h2.6M4.2 19.8l1.9-1.9M17.9 6.1l1.9-1.9" />
        </svg>
    );
}

function MoonIcon() {
    return (
        <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            focusable="false"
        >
            <path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1Z" />
        </svg>
    );
}

/** Light/dark switch; renders the icon of the mode it switches to. */
export default function ThemeToggle({ className = "" }) {
    const { theme, toggleTheme } = useTheme();
    const isDark = theme === DARK;
    const label = `Switch to ${isDark ? "light" : "dark"} mode`;

    return (
        <button
            type="button"
            className={["theme-toggle", className].filter(Boolean).join(" ")}
            onClick={toggleTheme}
            aria-label={label}
            title={label}
        >
            {isDark ? <SunIcon /> : <MoonIcon />}
        </button>
    );
}
