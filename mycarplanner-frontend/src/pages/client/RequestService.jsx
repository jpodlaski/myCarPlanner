import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import DropOffPicker from "../../components/DropOffPicker";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import "./RequestService.css";

const MAX_DESCRIPTION_WORDS = 500;

const DROPOFF_REQUIRED_MESSAGE =
    "Choose an estimated drop-off date and time so we know when to expect the car.";

const PARTIAL_DROPOFF_MESSAGE =
    "Choose both a drop-off date and a time.";

function wordCount(text) {
    const trimmed = text.trim();
    if (!trimmed) return 0;
    return trimmed.split(/\s+/).length;
}

function truncateToMaxWords(text, maxWords) {
    const trimmedEnd = text.replace(/\s+$/, "");
    if (wordCount(trimmedEnd) <= maxWords) return text;
    const words = trimmedEnd.trim().split(/\s+/).slice(0, maxWords);
    const endsWithWhitespace = /\s$/.test(text);
    return words.join(" ") + (endsWithWhitespace ? " " : "");
}

function friendlyBookingError(message) {
    if (!message) return "Could not create booking.";
    if (/estimatedDropOffTime/i.test(message)) {
        return DROPOFF_REQUIRED_MESSAGE;
    }
    return message;
}

/** Creates a SCHEDULED booking; estimated drop-off date and time are required. */
export default function RequestService() {
    const navigate = useNavigate();
    const session = getSession();

    const [vehicles, setVehicles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [scheduleError, setScheduleError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [vehicleId, setVehicleId] = useState("");
    const [description, setDescription] = useState("");
    const [dropOffDate, setDropOffDate] = useState("");
    const [dropOffClock, setDropOffClock] = useState("");
    const [availabilityNotes, setAvailabilityNotes] = useState("");

    const descriptionWords = useMemo(() => wordCount(description), [description]);

    const logout = () => {
        clearSession();
        navigate("/");
    };

    const loadVehicles = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            const res = await api.get("/vehicles/me");
            setVehicles(res.data);
            setVehicleId((current) => current || (res.data[0] ? String(res.data[0].id) : ""));
        } catch {
            setError("Could not load your vehicles.");
        } finally {
            setLoading(false);
        }
    }, [session?.token]);

    useEffect(() => {
        loadVehicles();
    }, [loadVehicles]);

    const submitBooking = async (e) => {
        e.preventDefault();
        setError("");
        setScheduleError("");

        if (!dropOffDate && !dropOffClock) {
            setScheduleError(DROPOFF_REQUIRED_MESSAGE);
            return;
        }

        if (!dropOffDate || !dropOffClock) {
            setScheduleError(PARTIAL_DROPOFF_MESSAGE);
            return;
        }

        setSubmitting(true);

        try {
            await api.post("/bookings", {
                vehicleId: Number(vehicleId),
                customerDescription: description,
                estimatedDropOffTime: `${dropOffDate}T${dropOffClock}:00`,
                availabilityNotes: availabilityNotes.trim() || undefined,
            });
            navigate("/client");
        } catch (err) {
            const apiMessage = err.response?.data?.message;
            const message = friendlyBookingError(apiMessage);
            if (/drop-off/i.test(message) || /estimatedDropOffTime/i.test(apiMessage ?? "")) {
                setScheduleError(message);
            } else {
                setError(message);
            }
        } finally {
            setSubmitting(false);
        }
    };

    const descriptionAtLimit = descriptionWords >= MAX_DESCRIPTION_WORDS;

    return (
        <AppShell
            pageTitle="Request service"
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
                <h1 className="page-title">Request service</h1>
                <p className="page-lede">
                    Tell us what needs attention and when you can bring the car in. A consultant
                    confirms the appointment.
                </p>
            </header>

            {loading && (
                <p className="status-text" role="status">
                    Loading…
                </p>
            )}

            {!loading && vehicles.length === 0 && (
                <div className="empty-state stack">
                    <p>Add a vehicle to your account before submitting a service request.</p>
                    <div>
                        <button
                            type="button"
                            className="button button-primary"
                            onClick={() => navigate("/client/add-vehicle")}
                        >
                            Add a vehicle
                        </button>
                    </div>
                </div>
            )}

            {!loading && vehicles.length > 0 && (
                <form className="request-form" onSubmit={submitBooking} noValidate>
                    <label className="field">
                        <span className="field-label">Vehicle</span>
                        <select
                            className="select"
                            value={vehicleId}
                            onChange={(e) => setVehicleId(e.target.value)}
                        >
                            {vehicles.map((v) => (
                                <option key={v.id} value={v.id}>
                                    {v.modelLine ?? v.model}
                                    {v.productionYear ? ` · ${v.productionYear}` : ""}
                                    {" · "}
                                    {v.vin}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className="field">
                        <span className="field-label">Describe the issue</span>
                        <textarea
                            className="textarea"
                            placeholder="What should the workshop look at?"
                            rows={4}
                            value={description}
                            maxLength={2000}
                            aria-describedby="description-count"
                            onChange={(e) => {
                                setDescription(
                                    truncateToMaxWords(e.target.value, MAX_DESCRIPTION_WORDS)
                                );
                            }}
                            required
                        />
                        <span
                            id="description-count"
                            className={descriptionAtLimit ? "field-error" : "field-hint"}
                        >
                            {descriptionWords} of {MAX_DESCRIPTION_WORDS} words
                        </span>
                    </label>

                    <fieldset
                        className={`request-fieldset${scheduleError ? " has-error" : ""}`}
                        aria-describedby={scheduleError ? "schedule-error" : undefined}
                    >
                        <legend className="field-label">
                            Estimated drop-off <span className="field-required">(required)</span>
                        </legend>
                        <DropOffPicker
                            dateValue={dropOffDate}
                            timeValue={dropOffClock}
                            onDateChange={(next) => {
                                setDropOffDate(next);
                                setDropOffClock("");
                                setScheduleError("");
                            }}
                            onTimeChange={(next) => {
                                setDropOffClock(next);
                                setScheduleError("");
                            }}
                            startHour={6}
                            endHour={20}
                        />
                    </fieldset>

                    {scheduleError && (
                        <p id="schedule-error" className="message message-error" role="alert">
                            {scheduleError}
                        </p>
                    )}

                    <label className="field">
                        <span className="field-label">
                            Availability notes <span className="field-required">(optional)</span>
                        </span>
                        <input
                            className="input"
                            placeholder="e.g. call if running late"
                            value={availabilityNotes}
                            onChange={(e) => setAvailabilityNotes(e.target.value)}
                        />
                    </label>

                    {error && (
                        <p className="message message-error" role="alert">
                            {error}
                        </p>
                    )}

                    <div className="button-row">
                        <button
                            type="submit"
                            className="button button-primary"
                            disabled={submitting}
                        >
                            {submitting ? "Submitting…" : "Submit request"}
                        </button>
                    </div>
                </form>
            )}

            {!session?.token && (
                <p className="message message-error" role="alert">
                    You must be signed in to request service.
                </p>
            )}
        </AppShell>
    );
}
