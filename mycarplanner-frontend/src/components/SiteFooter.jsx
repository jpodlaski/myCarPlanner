import { Link } from "react-router-dom";
import { WORKSHOP_ADDRESS } from "../constants/workshop";

const FOOTER_LINKS = [
    { to: "/login", label: "Sign in" },
    { to: "/register", label: "Create account" },
    { to: "/forgot-password", label: "Forgot password" },
    { to: "/resend-verification", label: "Resend verification" },
    { to: "/employee-login", label: "Staff access" },
];

export default function SiteFooter({ showLinks = true }) {
    const year = new Date().getFullYear();

    return (
        <footer className="site-footer">
            <div className="container container-wide">
                <p className="site-footer-note">
                    Appointment times are confirmed by the workshop once a consultant reviews your
                    request. Estimated costs may change after inspection; the final cost appears on
                    your invoice.
                </p>

                {showLinks && (
                    <nav aria-label="Footer" className="site-footer-nav">
                        <ul>
                            {FOOTER_LINKS.map((link) => (
                                <li key={link.to}>
                                    <Link to={link.to}>{link.label}</Link>
                                </li>
                            ))}
                        </ul>
                    </nav>
                )}

                <div className="site-footer-legal">
                    <p>Copyright © {year} myCarPlanner. All rights reserved.</p>
                    <address>Hussarya Service · {WORKSHOP_ADDRESS}</address>
                </div>
            </div>
        </footer>
    );
}
