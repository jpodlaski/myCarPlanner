import { Link, useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import VehicleConfigurator from "../../components/VehicleConfigurator";
import { clearSession, getSession } from "../../services/auth";
import "./RequestService.css";

/** Hosts VehicleConfigurator and returns to the client dashboard after saving. */
export default function AddVehicle() {
    const navigate = useNavigate();
    const session = getSession();

    const logout = () => {
        clearSession();
        navigate("/");
    };

    return (
        <AppShell
            pageTitle="Add a vehicle"
            title="Garage"
            homeTo="/client"
            userName={session?.name}
            onSignOut={logout}
            localAction={
                <Link to="/client" className="link-chevron link-chevron-back local-nav-back">
                    Back to Garage
                </Link>
            }
        >
            <header className="page-header">
                <h1 className="page-title">Add a vehicle</h1>
                <p className="page-lede">
                    Choose your Hussarya step by step, then enter its VIN.
                </p>
            </header>

            {session?.token ? (
                <VehicleConfigurator onVehicleAdded={() => navigate("/client")} />
            ) : (
                <p className="message message-error" role="alert">
                    You must be signed in to add a vehicle.
                </p>
            )}
        </AppShell>
    );
}
