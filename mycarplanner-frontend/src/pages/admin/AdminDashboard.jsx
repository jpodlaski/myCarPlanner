import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import DetailList from "../../components/DetailList";
import ExpandableRow from "../../components/ExpandableRow";
import Pagination from "../../components/Pagination";
import PasswordCard from "../../components/PasswordCard";
import StatusBadge from "../../components/StatusBadge";
import TextField from "../../components/TextField";
import { ASSIGNABLE_ROLES, roleLabel } from "../../constants/roles";
import { useFocusOnChange } from "../../hooks/useFocusOnChange";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import { formatCustomerDateTime } from "../../utils/dateTime";
import { formatEuro } from "../../utils/format";
import "./AdminDashboard.css";

const SECTIONS = {
    workers: "Workers",
    bookings: "Booking history",
    account: "Account",
};

const BOOKINGS_PAGE_SIZE = 20;

const LEDES = {
    workers: "Create staff accounts, change roles and remove access.",
    bookings: "Every booking across the workshop, newest first.",
    account: "Sign-in and account settings.",
};

function formatCancelledBy(value) {
    if (!value) return null;
    const labels = {
        CUSTOMER: "Client",
        WORKER: "Worker",
        WORKSHOP: "Consultant",
        CONSULTANT: "Consultant",
    };
    return labels[value] ?? value;
}

/**
 * Management dashboard: create/update/delete workers and view full booking history.
 * UI is gated by MANAGEMENT_ROLES; API still checks AuthSupport.requireCanManageWorkers().
 */
export default function AdminDashboard() {
    const navigate = useNavigate();
    const session = getSession();

    const [section, setSection] = useState("workers");
    const headingRef = useFocusOnChange(section);
    const [showAddWorkerForm, setShowAddWorkerForm] = useState(false);
    const [showPasswordForm, setShowPasswordForm] = useState(false);
    const [expandedWorkerId, setExpandedWorkerId] = useState(null);
    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [archiveQuery, setArchiveQuery] = useState("");
    const [bookingQuery, setBookingQuery] = useState("");
    const [bookingPage, setBookingPage] = useState(0);
    const [bookingTotals, setBookingTotals] = useState({ totalItems: 0, totalPages: 0 });

    const [workers, setWorkers] = useState([]);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [bookingsLoading, setBookingsLoading] = useState(true);
    const [error, setError] = useState("");
    const [bookingsError, setBookingsError] = useState("");
    const [formError, setFormError] = useState("");
    const [formSuccess, setFormSuccess] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [updatingRoleId, setUpdatingRoleId] = useState(null);

    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState("MECHANIC");

    const sessionToken = session?.token;

    const loadWorkers = useCallback(async () => {
        if (!sessionToken) return;
        setLoading(true);
        setError("");
        try {
            const res = await api.get("/admins/workers");
            setWorkers(res.data);
        } catch {
            setError("Could not load workers.");
        } finally {
            setLoading(false);
        }
    }, [sessionToken]);

    const loadBookings = useCallback(async () => {
        if (!sessionToken) return;
        setBookingsLoading(true);
        setBookingsError("");
        try {
            const res = await api.get("/admins/bookings", {
                params: { page: bookingPage, size: BOOKINGS_PAGE_SIZE, q: bookingQuery },
            });
            setBookings(res.data.items);
            setBookingTotals({ totalItems: res.data.totalItems, totalPages: res.data.totalPages });
        } catch {
            setBookingsError("Could not load booking history.");
        } finally {
            setBookingsLoading(false);
        }
    }, [sessionToken, bookingPage, bookingQuery]);

    useEffect(() => {
        loadWorkers();
    }, [loadWorkers]);

    useEffect(() => {
        loadBookings();
    }, [loadBookings]);

    // Wait for a pause in typing so each keystroke doesn't trigger a request.
    useEffect(() => {
        const next = archiveQuery.trim();
        if (next === bookingQuery) return undefined;
        const id = window.setTimeout(() => {
            setBookingQuery(next);
            setBookingPage(0);
        }, 300);
        return () => window.clearTimeout(id);
    }, [archiveQuery, bookingQuery]);

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const selectSection = (next) => {
        setSection(next);
        setExpandedWorkerId(null);
        setExpandedBookingId(null);
        if (next !== "bookings") setArchiveQuery("");
        if (next !== "workers") {
            setShowAddWorkerForm(false);
            setFormError("");
            setFormSuccess("");
        }
        if (next !== "account") setShowPasswordForm(false);
    };

    const createWorker = async (e) => {
        e.preventDefault();
        setFormError("");
        setFormSuccess("");
        setSubmitting(true);

        try {
            await api.post("/admins/workers", {
                firstName,
                lastName,
                email,
                login,
                password,
                role,
            });
            setFormSuccess("Worker account created.");
            setFirstName("");
            setLastName("");
            setEmail("");
            setLogin("");
            setPassword("");
            setRole("MECHANIC");
            setShowAddWorkerForm(false);
            loadWorkers();
        } catch (err) {
            setFormError(err.response?.data?.message ?? "Could not create worker.");
        } finally {
            setSubmitting(false);
        }
    };

    const deleteWorker = async (worker) => {
        const confirmed = window.confirm(
            `Delete ${worker.firstName} ${worker.lastName} (${roleLabel(worker.role)})?`
        );
        if (!confirmed) return;

        setDeletingId(worker.id);
        setError("");

        try {
            await api.delete(`/admins/workers/${worker.id}`);
            loadWorkers();
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not delete worker.");
        } finally {
            setDeletingId(null);
        }
    };

    const changeWorkerRole = async (worker, newRole) => {
        if (newRole === worker.role) return;

        setUpdatingRoleId(worker.id);
        setError("");

        try {
            await api.patch(`/admins/workers/${worker.id}/role`, {
                role: newRole,
            });
            loadWorkers();
        } catch (err) {
            setError(err.response?.data?.message ?? "Could not update role.");
        } finally {
            setUpdatingRoleId(null);
        }
    };

    const bookingDetailItems = (booking) => [
        { label: "Customer", value: booking.customerName },
        { label: "Email", value: booking.customerEmail },
        { label: "Issue", value: booking.customerDescription },
        { label: "Vehicle", value: booking.modelLine ?? booking.carModel },
        { label: "VIN", value: booking.vin },
        {
            label: "Services",
            value: booking.serviceTypes?.length > 0 ? booking.serviceTypes.join(", ") : null,
        },
        {
            label: "Preferred drop-off",
            value: booking.estimatedDropOffTime
                ? formatCustomerDateTime(booking.estimatedDropOffTime)
                : null,
        },
        {
            label: "Appointment",
            value: booking.scheduledDateTime
                ? formatCustomerDateTime(booking.scheduledDateTime)
                : null,
        },
        {
            label: "Assigned worker",
            value: booking.assignedWorkerName
                ? `${booking.assignedWorkerName}${
                    booking.assignedWorkerRole ? ` (${roleLabel(booking.assignedWorkerRole)})` : ""
                }`
                : null,
        },
        { label: "Availability notes", value: booking.availabilityNotes?.trim() || null },
        { label: "Estimated cost", value: formatEuro(booking.estimatedCost) },
        { label: "Final cost", value: formatEuro(booking.finalCost) },
        {
            label: "Cancelled by",
            value: formatCancelledBy(booking.cancelledBy),
            hidden: !(booking.cancelledBy || booking.cancellationReason),
        },
        {
            label: "Cancellation reason",
            value: booking.cancellationReason?.trim() || null,
            hidden: !(booking.cancelledBy || booking.cancellationReason),
        },
    ];

    return (
        <AppShell
            pageTitle={SECTIONS[section]}
            title="Management"
            homeTo="/admin"
            userName={session?.name}
            onSignOut={logout}
            sections={SECTIONS}
            activeSection={section}
            onSelectSection={selectSection}
        >
            <header className="page-header">
                <h1 ref={headingRef} tabIndex={-1} className="page-title">
                    {SECTIONS[section]}
                </h1>
                <p className="page-lede">{LEDES[section]}</p>
            </header>

            {section === "workers" && (
                <div className="stack">
                    {!showAddWorkerForm ? (
                        <div className="button-row">
                            <button
                                type="button"
                                className="button button-secondary"
                                onClick={() => {
                                    setFormError("");
                                    setFormSuccess("");
                                    setShowAddWorkerForm(true);
                                }}
                            >
                                Add worker
                            </button>
                        </div>
                    ) : (
                        <section className="card" aria-labelledby="add-worker-title">
                            <div className="card-header">
                                <h2 id="add-worker-title" className="card-title">
                                    Add worker
                                </h2>
                                <button
                                    type="button"
                                    className="button button-neutral button-small"
                                    onClick={() => {
                                        setShowAddWorkerForm(false);
                                        setFormError("");
                                        setFormSuccess("");
                                    }}
                                >
                                    Cancel
                                </button>
                            </div>
                            <form className="form-stack" onSubmit={createWorker}>
                                <div className="form-grid">
                                    <TextField
                                        label="First name"
                                        autoComplete="off"
                                        value={firstName}
                                        onChange={(e) => setFirstName(e.target.value)}
                                        required
                                    />
                                    <TextField
                                        label="Last name"
                                        autoComplete="off"
                                        value={lastName}
                                        onChange={(e) => setLastName(e.target.value)}
                                        required
                                    />
                                    <TextField
                                        label="Email"
                                        type="email"
                                        autoComplete="off"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                    />
                                    <TextField
                                        label="Login"
                                        autoComplete="off"
                                        autoCapitalize="none"
                                        spellCheck={false}
                                        value={login}
                                        onChange={(e) => setLogin(e.target.value)}
                                        required
                                    />
                                    <TextField
                                        label="Initial password"
                                        type="password"
                                        autoComplete="new-password"
                                        hint="At least 8 characters."
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        minLength={8}
                                    />
                                </div>
                                <label className="field worker-role-field">
                                    <span className="field-label">Role</span>
                                    <select
                                        className="select"
                                        value={role}
                                        onChange={(e) => setRole(e.target.value)}
                                    >
                                        {ASSIGNABLE_ROLES.map((option) => (
                                            <option key={option} value={option}>
                                                {roleLabel(option)}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                {formError && (
                                    <p className="message message-error" role="alert">
                                        {formError}
                                    </p>
                                )}
                                <div className="button-row">
                                    <button type="submit" className="button button-primary" disabled={submitting}>
                                        {submitting ? "Creating…" : "Create worker"}
                                    </button>
                                </div>
                            </form>
                        </section>
                    )}

                    <div role="status">
                        {formSuccess && <p className="message message-success">{formSuccess}</p>}
                    </div>

                    {loading && (
                        <p className="status-text" role="status">
                            Loading…
                        </p>
                    )}
                    {error && (
                        <p className="message message-error" role="alert">
                            {error}
                        </p>
                    )}

                    {!loading && !error && workers.length === 0 && (
                        <p className="empty-state">No workers yet.</p>
                    )}

                    {workers.length > 0 && (
                        <ul className="row-list">
                            {workers.map((worker) => {
                                const expanded = expandedWorkerId === worker.id;
                                const roleSelectId = `worker-role-${worker.id}`;
                                return (
                                    <ExpandableRow
                                        key={worker.id}
                                        eyebrow={roleLabel(worker.role)}
                                        title={`${worker.firstName} ${worker.lastName}`}
                                        meta={worker.login}
                                        expanded={expanded}
                                        onToggle={() =>
                                            setExpandedWorkerId((current) =>
                                                current === worker.id ? null : worker.id
                                            )
                                        }
                                    >
                                        <DetailList
                                            items={[
                                                { label: "Role", value: roleLabel(worker.role) },
                                                { label: "Login", value: worker.login },
                                                { label: "Email", value: worker.email },
                                            ]}
                                        />
                                        <div className="row-section worker-actions">
                                            <div className="field">
                                                <label className="field-label" htmlFor={roleSelectId}>
                                                    Change role
                                                </label>
                                                <select
                                                    id={roleSelectId}
                                                    className="select"
                                                    value={worker.role ?? "MECHANIC"}
                                                    disabled={updatingRoleId === worker.id}
                                                    onChange={(e) =>
                                                        changeWorkerRole(worker, e.target.value)
                                                    }
                                                >
                                                    {ASSIGNABLE_ROLES.map((option) => (
                                                        <option key={option} value={option}>
                                                            {roleLabel(option)}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>
                                            <button
                                                type="button"
                                                className="button button-danger"
                                                disabled={deletingId === worker.id}
                                                onClick={() => deleteWorker(worker)}
                                            >
                                                {deletingId === worker.id ? "Deleting…" : "Delete worker"}
                                            </button>
                                        </div>
                                    </ExpandableRow>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}

            {section === "bookings" && (
                <div className="stack">
                    {bookingsLoading && (
                        <p className="status-text" role="status">
                            Loading…
                        </p>
                    )}
                    {bookingsError && (
                        <p className="message message-error" role="alert">
                            {bookingsError}
                        </p>
                    )}
                    {(bookingTotals.totalItems > 0 || bookingQuery) && (
                        <div className="toolbar">
                            <label className="field">
                                <span className="field-label">Search bookings</span>
                                <input
                                    className="input"
                                    type="search"
                                    placeholder="Booking number, customer, vehicle, VIN, status"
                                    value={archiveQuery}
                                    onChange={(e) => setArchiveQuery(e.target.value)}
                                />
                            </label>
                        </div>
                    )}
                    {!bookingsLoading && !bookingsError && bookings.length === 0 && !bookingQuery && (
                        <p className="empty-state">No bookings yet.</p>
                    )}
                    {!bookingsLoading && !bookingsError && bookings.length === 0 && bookingQuery && (
                        <p className="empty-state" role="status">
                            No bookings match “{bookingQuery}”.
                        </p>
                    )}
                    {bookings.length > 0 && (
                        <ul className="row-list">
                            {bookings.map((booking) => (
                                <ExpandableRow
                                    key={booking.id}
                                    eyebrow={`Booking #${booking.id}`}
                                    title={booking.modelLine ?? booking.carModel}
                                    meta={[
                                        booking.vin && `VIN ${booking.vin}`,
                                        booking.customerName,
                                        booking.scheduledDateTime
                                            && `Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`,
                                        booking.assignedWorkerName,
                                        booking.status === "CANCELLED"
                                            && formatCancelledBy(booking.cancelledBy)
                                            && `Cancelled by ${formatCancelledBy(booking.cancelledBy)}`,
                                        booking.status === "COMPLETED"
                                            && booking.finalCost != null
                                            && `${booking.finalCost} EUR`,
                                    ].filter(Boolean).join(" · ")}
                                    badge={<StatusBadge status={booking.status} />}
                                    expanded={expandedBookingId === booking.id}
                                    onToggle={() =>
                                        setExpandedBookingId((current) =>
                                            current === booking.id ? null : booking.id
                                        )
                                    }
                                >
                                    <DetailList items={bookingDetailItems(booking)} />
                                </ExpandableRow>
                            ))}
                        </ul>
                    )}
                    <Pagination
                        page={bookingPage}
                        totalPages={bookingTotals.totalPages}
                        totalItems={bookingTotals.totalItems}
                        itemLabel={bookingTotals.totalItems === 1 ? "booking" : "bookings"}
                        label="Booking history pages"
                        disabled={bookingsLoading}
                        onChange={(next) => {
                            setExpandedBookingId(null);
                            setBookingPage(next);
                        }}
                    />
                </div>
            )}

            {section === "account" && (
                <PasswordCard open={showPasswordForm} onOpenChange={setShowPasswordForm} />
            )}
        </AppShell>
    );
}
