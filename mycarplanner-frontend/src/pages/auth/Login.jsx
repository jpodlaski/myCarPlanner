import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../../services/api";
import { saveSession } from "../../services/auth";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

/** Client login; email is used as the login field. */
export default function Login() {
    const navigate = useNavigate();
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleLogin = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");

        try {
            const res = await api.post("/auth/login", { login, password });
            saveSession(res.data);
            navigate("/client");
        } catch (err) {
            setError(err.response?.data?.message ?? "Login failed.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Sign in"
            lede="Manage your Hussarya, request service and follow your bookings."
            links={[
                { to: "/forgot-password", label: "Forgot password?" },
                { to: "/register", label: "Create account" },
                { to: "/", label: "Back to home" },
            ]}
        >
            <form className="form-stack" onSubmit={handleLogin}>
                <TextField
                    label="Email"
                    type="email"
                    autoComplete="email"
                    value={login}
                    onChange={(e) => setLogin(e.target.value)}
                    required
                />
                <TextField
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />
                {error && (
                    <p className="message message-error" role="alert">
                        {error}
                    </p>
                )}
                <button type="submit" className="button button-primary button-block" disabled={loading}>
                    {loading ? "Signing in…" : "Sign in"}
                </button>
            </form>
        </AuthLayout>
    );
}
