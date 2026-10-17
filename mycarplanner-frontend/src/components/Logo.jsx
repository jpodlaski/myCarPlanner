import { DARK } from "../theme/theme";
import { useTheme } from "../theme/useTheme";

/** Brand lock-up: logo.svg for the light theme, logo-inverse.svg for the dark one. */
export default function Logo({ className = "" }) {
    const { theme } = useTheme();
    const src = theme === DARK ? "/logo-inverse.svg" : "/logo.svg";

    return (
        <img
            className={["logo-mark", className].filter(Boolean).join(" ")}
            src={src}
            alt="myCarPlanner"
        />
    );
}
