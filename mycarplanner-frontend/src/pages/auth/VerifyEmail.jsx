import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import AuthLayout from "../../components/AuthLayout";

export default function VerifyEmail() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(Boolean(token));

    useEffect(() => {
        if (!token) {
            setError("Missing verification token. Open the link from your email.");
            setLoading(false);
            return;
        }

        let cancelled = false;
        (async () => {
            try {
                const res = await api.post("/auth/verify-email", { token });
                if (!cancelled) {
                    setMessage(res.data?.message ?? "Email verified.");
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err.response?.data?.message ?? "Could not verify email.");
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [token]);

    return (
        <AuthLayout
            title="Verify email"
            links={[
                { to: "/login", label: "Sign in" },
                { to: "/resend-verification", label: "Resend verification" },
            ]}
        >
            <div role="status">
                {loading && <p className="message message-info">Verifying your email…</p>}
                {message && <p className="message message-success">{message}</p>}
            </div>
            {error && (
                <p className="message message-error" role="alert">
                    {error}
                </p>
            )}
        </AuthLayout>
    );
}
