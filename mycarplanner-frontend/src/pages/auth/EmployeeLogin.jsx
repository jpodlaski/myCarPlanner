import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../../services/api";
import { getDashboardPathForRole, saveSession } from "../../services/auth";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

/** Staff login; the returned role picks the /admin or /employee dashboard. */
export default function EmployeeLogin() {
    const navigate = useNavigate();
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const loginEmployee = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");

        try {
            const res = await api.post("/auth/employee-login", { login, password });
            saveSession(res.data);
            navigate(getDashboardPathForRole(res.data.role));
        } catch (err) {
            if (!err.response) {
                setError("Could not reach the server. Check that the API is running on port 8080.");
            } else {
                setError(err.response?.data?.message ?? "Login failed.");
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Staff sign in"
            lede="For workshop consultants, technicians and management."
            links={[{ to: "/", label: "Back to home" }]}
        >
            <form className="form-stack" onSubmit={loginEmployee}>
                <TextField
                    label="Login"
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
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
