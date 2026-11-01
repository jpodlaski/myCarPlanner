import { Component } from "react";
import Logo from "./Logo";
import "./Shell.css";
import "../pages/auth/AuthShell.css";

/**
 * Last-resort screen when rendering throws. It avoids router links and the global nav,
 * because the crash may have come from them.
 */
export default class ErrorBoundary extends Component {
    state = { error: null };

    static getDerivedStateFromError(error) {
        return { error };
    }

    componentDidCatch(error, info) {
        console.error("Unhandled UI error", error, info.componentStack);
    }

    componentDidUpdate() {
        if (this.state.error) {
            document.title = "Something went wrong – myCarPlanner";
        }
    }

    render() {
        if (!this.state.error) {
            return this.props.children;
        }

        return (
            <main id="main" className="auth-main">
                <div className="auth-panel" role="alert">
                    <Logo className="auth-logo" alt="" />
                    <h1 className="auth-title">Something went wrong.</h1>
                    <p className="auth-lede">
                        This page stopped working. Reloading usually fixes it; your bookings are safe.
                    </p>
                    <div className="button-row error-actions">
                        <button
                            type="button"
                            className="button button-primary"
                            onClick={() => window.location.reload()}
                        >
                            Reload page
                        </button>
                        <a href="/" className="link-chevron">
                            Go to the home page
                        </a>
                    </div>
                </div>
            </main>
        );
    }
}
