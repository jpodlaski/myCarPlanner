import { useState } from "react";
import { api } from "../services/api";
import TextField from "./TextField";

/** Self-service password change shared by the client, worker and admin dashboards. */
export default function ChangePasswordForm() {
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);

    // Show mismatch as soon as the user has typed something in confirm.
    const passwordsMismatch =
        confirmPassword.length > 0 && newPassword !== confirmPassword;
    const canSubmit =
        !loading
        && currentPassword.length > 0
        && newPassword.length >= 8
        && confirmPassword.length > 0
        && !passwordsMismatch;

    const clearFeedback = () => {
        if (error) setError("");
        if (success) setSuccess("");
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");
        setSuccess("");

        if (newPassword !== confirmPassword) {
            setError("New passwords do not match.");
            return;
        }

        setLoading(true);
        try {
            await api.post("/auth/change-password", {
                currentPassword,
                newPassword,
            });
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
            setSuccess("Password updated.");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not change password.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <form className="form-stack change-password-form" onSubmit={handleSubmit}>
            <p className="card-text">
                Enter your current password and choose a new one (at least 8 characters).
            </p>
            <TextField
                label="Current password"
                type="password"
                value={currentPassword}
                onChange={(e) => {
                    clearFeedback();
                    setCurrentPassword(e.target.value);
                }}
                autoComplete="current-password"
                required
            />
            <TextField
                label="New password"
                type="password"
                value={newPassword}
                onChange={(e) => {
                    clearFeedback();
                    setNewPassword(e.target.value);
                }}
                autoComplete="new-password"
                minLength={8}
                required
            />
            <TextField
                label="Confirm new password"
                type="password"
                value={confirmPassword}
                onChange={(e) => {
                    clearFeedback();
                    setConfirmPassword(e.target.value);
                }}
                autoComplete="new-password"
                minLength={8}
                required
                error={passwordsMismatch ? "Passwords do not match." : undefined}
            />
            {error && (
                <p className="message message-error" role="alert">
                    {error}
                </p>
            )}
            {success && (
                <p className="message message-success" role="status">
                    {success}
                </p>
            )}
            <div className="button-row">
                <button type="submit" className="button button-primary" disabled={!canSubmit}>
                    {loading ? "Updating…" : "Update password"}
                </button>
            </div>
        </form>
    );
}
