import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { clearSession } from "../../services/auth";
import AuthLayout from "../../components/AuthLayout";

export default function ConfirmAccountDeletion() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") ?? "";
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(Boolean(token));

    useEffect(() => {
        if (!token) {
            setError("Missing confirmation token. Open the link from your email.");
            setLoading(false);
            return;
        }

        let cancelled = false;
        (async () => {
            try {
                const res = await api.post(
                    "/auth/confirm-account-deletion",
                    { token },
                    { skipAuthRedirect: true }
                );
                if (!cancelled) {
                    clearSession();
                    setMessage(res.data?.message ?? "Your account has been deleted.");
                }
            } catch (err) {
                if (!cancelled) {
                    setError(
                        err.response?.data?.message ?? "Could not delete account."
                    );
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

    const links = [{ to: "/", label: "Back to home" }];
    if (!message) links.push({ to: "/login", label: "Sign in" });

    return (
        <AuthLayout title="Delete account" links={links}>
            <div role="status">
                {loading && <p className="message message-info">Confirming account deletion…</p>}
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
