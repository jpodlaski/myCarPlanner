import { useState } from "react";
import { api } from "../../services/api";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

export default function ResendVerification() {
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
            const res = await api.post("/auth/resend-verification", { email });
            setMessage(res.data?.message ?? "If that email needs verification, a link was sent.");
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not resend verification.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Resend verification"
            lede="Enter your email if you still need to verify your account."
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
                    {loading ? "Sending…" : "Resend link"}
                </button>
            </form>
        </AuthLayout>
    );
}
