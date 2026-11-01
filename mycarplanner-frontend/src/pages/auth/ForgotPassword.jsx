import { useState } from "react";
import { api } from "../../services/api";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

export default function ForgotPassword() {
    const [email, setEmail] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        setMessage("");

        try {
            const res = await api.post("/auth/forgot-password", { email });
            setMessage(res.data?.message ?? "If that email is registered, you will receive a reset link.");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not request a password reset.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Forgot password"
            lede="Enter your account email and we will send a reset link if it exists."
            links={[{ to: "/login", label: "Back to sign in" }]}
        >
            <form className="form-stack" onSubmit={submit}>
                <TextField
                    label="Email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
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
                <button type="submit" className="button button-primary button-block" disabled={loading}>
                    {loading ? "Sending…" : "Send reset link"}
                </button>
            </form>
        </AuthLayout>
    );
}
