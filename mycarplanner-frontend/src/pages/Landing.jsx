import { Link } from "react-router-dom";
import "./Landing.css";
import Logo from "../components/Logo";
import ThemeToggle from "../components/ThemeToggle";

/** Public home — same shell and typography language as signed-in dashboards. */
export default function Landing() {
    return (
        <div className="landing">
            <div className="landing-inner">
                <header className="landing-header">
                    <Link to="/" className="logo" aria-label="myCarPlanner home">
                        <Logo />
                    </Link>

                    <nav className="landing-actions" aria-label="Account">
                        <ThemeToggle />
                        <Link to="/login" className="landing-btn landing-btn-solid">
                            Login
                        </Link>
                        <Link to="/register" className="landing-btn landing-btn-ghost">
                            Register
                        </Link>
                        <span className="landing-actions-sep" aria-hidden="true">
                            |
                        </span>
                        <Link to="/employee-login" className="landing-staff-link">
                            Staff access
                        </Link>
                    </nav>
                </header>

                <main className="landing-main">
                    <h1 className="landing-title">Plan care for your vehicle</h1>
                    <p className="landing-lede">
                        Register, add your car, and request service in one place. Track
                        appointments from workshop confirmation through completion and invoice.
                    </p>

                    <div className="landing-cta-row">
                        <Link to="/register" className="landing-btn landing-btn-solid">
                            Create account
                        </Link>
                        <Link to="/login" className="landing-btn landing-btn-ghost">
                            Sign in
                        </Link>
                    </div>
                </main>
            </div>
        </div>
    );
}
