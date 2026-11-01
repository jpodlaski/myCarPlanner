import { useId } from "react";
import ChangePasswordForm from "./ChangePasswordForm";

export default function PasswordCard({ open, onOpenChange }) {
    const titleId = useId();

    return (
        <section className="card" aria-labelledby={titleId}>
            <div className="card-header">
                <h2 id={titleId} className="card-title">
                    Password
                </h2>
                {open ? (
                    <button
                        type="button"
                        className="button button-neutral button-small"
                        onClick={() => onOpenChange(false)}
                    >
                        Cancel
                    </button>
                ) : (
                    <button
                        type="button"
                        className="button button-secondary button-small"
                        onClick={() => onOpenChange(true)}
                    >
                        Change password
                    </button>
                )}
            </div>
            {open ? (
                <ChangePasswordForm />
            ) : (
                <p className="card-text">Update the password you use to sign in.</p>
            )}
        </section>
    );
}
