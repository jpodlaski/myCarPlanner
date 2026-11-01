import AuthLayout from "../components/AuthLayout";

export default function NotFound() {
    return (
        <AuthLayout
            title="Page not found"
            lede="The page you’re looking for doesn’t exist or has moved."
            links={[
                { to: "/", label: "Go to the home page" },
                { to: "/login", label: "Sign in" },
            ]}
        />
    );
}
