import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../../services/api";
import AuthLayout from "../../components/AuthLayout";
import TextField from "../../components/TextField";

/** Client self-registration; the email must be verified before signing in. */
export default function Register() {
    const navigate = useNavigate();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    const register = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        setMessage("");

        try {
            const res = await api.post("/auth/register", {
                firstName,
                lastName,
                email,
                password,
            });
            setMessage(res.data?.message ?? "Account created. Check your email to verify.");
            setTimeout(() => navigate("/login"), 2500);
        } catch (err) {
            setError(err.response?.data?.message ?? "Registration failed.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Create your account"
            lede="One account for every Hussarya you own."
            links={[
                { to: "/login", label: "Already have an account? Sign in" },
                { to: "/resend-verification", label: "Resend verification email" },
                { to: "/", label: "Back to home" },
            ]}
        >
            <form className="form-stack" onSubmit={register}>
                <div className="form-grid">
                    <TextField
                        label="First name"
                        autoComplete="given-name"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        required
                    />
                    <TextField
                        label="Last name"
                        autoComplete="family-name"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        required
                    />
                </div>
                <TextField
                    label="Email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                />
                <TextField
                    label="Password"
                    type="password"
                    autoComplete="new-password"
                    hint="At least 8 characters."
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    minLength={8}
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
                    {loading ? "Creating account…" : "Continue"}
                </button>
            </form>
        </AuthLayout>
    );
}
