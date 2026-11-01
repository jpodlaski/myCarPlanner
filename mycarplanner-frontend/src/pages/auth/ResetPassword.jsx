import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

export default function ResetPassword() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        setError("");
        setMessage("");

        if (!token) {
            setError("Missing reset token. Open the link from your email.");
            return;
        }
        if (newPassword !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setLoading(true);
        try {
            const res = await api.post("/auth/reset-password", { token, newPassword });
            setMessage(res.data?.message ?? "Password updated.");
            setNewPassword("");
            setConfirmPassword("");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not reset password.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Reset password"
            lede="Choose a new password (at least 8 characters)."
            links={[{ to: "/login", label: "Back to sign in" }]}
        >
            <form className="form-stack" onSubmit={submit}>
                <TextField
                    label="New password"
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    minLength={8}
                    autoComplete="new-password"
                />
                <TextField
                    label="Confirm new password"
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    minLength={8}
                    autoComplete="new-password"
                />
                {message && (
                    <p className="message message-success" role="status">
                        {message}
                    </p>
                )}
                {error && (
                    <p className="message message-error" role="alert">
                        {error}
                    </p>
                )}
                <button
                    type="submit"
                    className="button button-primary button-block"
                    disabled={loading || !token}
                >
                    {loading ? "Updating…" : "Update password"}
                </button>
            </form>
        </AuthLayout>
    );
}
