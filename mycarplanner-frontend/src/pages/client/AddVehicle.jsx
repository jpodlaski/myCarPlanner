import { useNavigate } from "react-router-dom";
import VehicleConfigurator from "../../components/VehicleConfigurator";
import { getSession } from "../../services/auth";
import "../../components/VehicleConfigurator.css";
import "../../components/DashboardLayout.css";
import "./ClientDashboard.css";
import "./AddVehicle.css";
import Logo from "../../components/Logo";
import ThemeToggle from "../../components/ThemeToggle";

// Wrapper around VehicleConfigurator; returns to client dashboard after success.
export default function AddVehicle() {
    const navigate = useNavigate();
    const session = getSession();

    return (
        <div className="client-dashboard-shell">
            <div className="dashboard client-dashboard add-vehicle-page">
                <div className="dashboard-header">
                    <div>
                        <Logo className="logo" />
                        <h1>Add a vehicle</h1>
                    </div>
                    <div className="dashboard-header-actions">
                        <ThemeToggle />
                        <button
                            type="button"
                            className="logout-btn"
                            onClick={() => navigate("/client")}
                        >
                            Back to dashboard
                        </button>
                    </div>
                </div>

                {session?.token ? (
                    <VehicleConfigurator onVehicleAdded={() => navigate("/client")} />
                ) : (
                    <p className="form-error">You must be signed in to add a vehicle.</p>
                )}
            </div>
        </div>
    );
}
