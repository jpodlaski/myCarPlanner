import { useCallback, useEffect, useMemo, useState } from "react";

import { ThemeContext } from "./ThemeContext";
import {
    DARK,
    LIGHT,
    applyTheme,
    resolveInitialTheme,
    storeTheme,
} from "./theme";

/**
 * Owns the active theme: stored choice first, otherwise light.
 */
export default function ThemeProvider({ children }) {
    const [theme, setTheme] = useState(resolveInitialTheme);

    useEffect(() => {
        applyTheme(theme);
    }, [theme]);

    const toggleTheme = useCallback(() => {
        const next = theme === DARK ? LIGHT : DARK;
        storeTheme(next);
        setTheme(next);
    }, [theme]);

    const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme]);

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
