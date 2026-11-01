import { useEffect, useId, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import Logo from "./Logo";
import ThemeToggle from "./ThemeToggle";

/**
 * Translucent top bar. `links` collapse behind a disclosure button on narrow screens;
 * `actions` (sign in / sign out) always stay visible.
 */
export default function GlobalNav({ links = [], actions = null, homeTo = "/" }) {
    const [open, setOpen] = useState(false);
    const panelId = useId();
    const location = useLocation();

    useEffect(() => {
        setOpen(false);
    }, [location.pathname, location.hash]);

    useEffect(() => {
        if (!open) return undefined;
        const onKeyDown = (event) => {
            if (event.key === "Escape") setOpen(false);
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [open]);

    return (
        <header className={`global-nav${open ? " global-nav-open" : ""}`}>
            <nav className="global-nav-inner container container-wide" aria-label="Global">
                <Link to={homeTo} className="global-nav-home">
                    <Logo variant="emblem" alt="Hussarya home" className="global-nav-logo" />
                </Link>

                {links.length > 0 && (
                    <>
                        <ul className="global-nav-links" id={panelId}>
                            {links.map((link) => (
                                <li key={link.to}>
                                    <Link to={link.to} className="global-nav-link">
                                        {link.label}
                                    </Link>
                                </li>
                            ))}
                        </ul>
                        <button
                            type="button"
                            className="global-nav-menu-btn"
                            aria-expanded={open}
                            aria-controls={panelId}
                            onClick={() => setOpen((value) => !value)}
                        >
                            <span className="visually-hidden">{open ? "Close menu" : "Menu"}</span>
                            <span className="global-nav-menu-icon" aria-hidden="true" />
                        </button>
                    </>
                )}

                <div className="global-nav-actions">
                    <ThemeToggle />
                    {actions}
                </div>
            </nav>
        </header>
    );
}
