import { Link } from "react-router-dom";
import { usePageTitle } from "../hooks/usePageTitle";
import GlobalNav from "./GlobalNav";
import { PUBLIC_LINKS } from "../constants/navigation";
import Logo from "./Logo";
import SiteFooter from "./SiteFooter";
import SkipLink from "./SkipLink";
import "./Shell.css";
import "../pages/auth/AuthShell.css";

/** Centred account panel used by every sign-in / recovery page. */
export default function AuthLayout({ title, lede, children, links }) {
    usePageTitle(title);

    return (
        <>
            <SkipLink />
            <GlobalNav
                links={PUBLIC_LINKS}
                actions={
                    <Link to="/login" className="global-nav-action">
                        Sign in
                    </Link>
                }
            />
            <main id="main" tabIndex={-1} className="auth-main">
                <div className="auth-panel">
                    <Logo className="auth-logo" alt="" />
                    <h1 className="auth-title">{title}</h1>
                    {lede && <p className="auth-lede">{lede}</p>}
                    {children && <div className="auth-body">{children}</div>}
                    {links?.length > 0 && (
                        <ul className="auth-links">
                            {links.map((link) => (
                                <li key={link.to}>
                                    <Link to={link.to} className="link-chevron">
                                        {link.label}
                                    </Link>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </main>
            <SiteFooter />
        </>
    );
}
