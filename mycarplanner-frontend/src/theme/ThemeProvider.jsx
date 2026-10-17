import { useCallback, useEffect, useMemo, useState } from "react";

import { ThemeContext } from "./ThemeContext";
import {
    DARK,
    LIGHT,
    applyTheme,
    getStoredTheme,
    resolveInitialTheme,
    storeTheme,
    watchSystemTheme,
} from "./theme";

/**
 * Owns the active theme: stored choice first, else the OS setting.
 * Toggling explicitly stops the provider following the OS.
 */
export default function ThemeProvider({ children }) {
    const [theme, setTheme] = useState(resolveInitialTheme);
    const [followsSystem, setFollowsSystem] = useState(() => getStoredTheme() === null);

    useEffect(() => {
        applyTheme(theme);
    }, [theme]);

    useEffect(() => {
        if (!followsSystem) return undefined;
        return watchSystemTheme(setTheme);
    }, [followsSystem]);

    const toggleTheme = useCallback(() => {
        const next = theme === DARK ? LIGHT : DARK;
        storeTheme(next);
        setFollowsSystem(false);
        setTheme(next);
    }, [theme]);

    const value = useMemo(
        () => ({ theme, toggleTheme, followsSystem }),
        [theme, toggleTheme, followsSystem],
    );

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
