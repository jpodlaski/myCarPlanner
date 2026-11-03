import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import DetailList from "../../components/DetailList";
import ExpandableRow from "../../components/ExpandableRow";
import PasswordCard from "../../components/PasswordCard";
import StatusBadge from "../../components/StatusBadge";
import { isConsultant, isTechnician } from "../../constants/roles";
import { useFocusOnChange } from "../../hooks/useFocusOnChange";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import {
    formatCustomerDateTime,
    hasCustomerAvailability,
    toDatetimeLocalValue,
} from "../../utils/dateTime";
import { formatEuro, formatStatus } from "../../utils/format";
import "./EmployeeDashboard.css";

function sectionLabels(consultant) {
    return {
        available: consultant ? "Incoming requests" : "Available jobs",
        assignments: consultant ? "Workshop" : "My assignments",
        archive: "Archive",
        account: "Account",
    };
}

function sectionLedes(consultant) {
    return {
        available: consultant
            ? "New service requests waiting for an appointment."
            : "Confirmed bookings ready for a technician to claim.",
        assignments: consultant
            ? "Accepted bookings waiting for, or with, a technician."
            : "Jobs you are working on now.",
        archive: "Completed and cancelled bookings.",
        account: "Sign-in and account settings.",
    };
}

const WORK_PLAN_SAVED = "Work planned updated.";

function bookingVin(booking) {
    return booking.vehicle?.vin ?? booking.vin ?? null;
}

function assignedWorkerName(booking) {
    const worker = booking.assignedWorker;
    if (!worker) return null;
    const name = [worker.firstName, worker.lastName].filter(Boolean).join(" ").trim();
    return name || worker.login || null;
}

function cancelledByLabel(cancelledBy) {
    if (cancelledBy === "CUSTOMER") return "Client";
    if (cancelledBy === "WORKER") return "Worker";
    if (cancelledBy === "WORKSHOP" || cancelledBy === "CONSULTANT") return "Consultant";
    return null;
}

function isArchiveStatus(status) {
    return status === "COMPLETED" || status === "CANCELLED";
}

function bookingMatchesQuery(booking, query) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const vin = bookingVin(booking);
    const worker = assignedWorkerName(booking);
    const cancelledBy = cancelledByLabel(booking.cancelledBy);
    const haystack = [
        booking.id,
        booking.status,
        formatStatus(booking.status),
        booking.customerName,
        booking.carModel,
        vin,
        worker,
        cancelledBy,
        booking.customerDescription,
        booking.cancellationReason,
        ...(booking.serviceTypes ?? []),
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
    return haystack.includes(q);
}

function joinMeta(parts) {
    return parts.filter(Boolean).join(" · ");
}

// A failed capacity lookup must not block Accept: the server re-checks capacity on accept.
function canAccept(form, capacity) {
    if (!form?.scheduledDateTime || !capacity) return false;
    return capacity.error === true || capacity.available === true;
}

function CapacityStatus({ capacity }) {
    let text;
    let tone = "";
    if (!capacity) {
        text = "Checking mechanic availability…";
    } else if (capacity.error) {
        text = "Could not check mechanic availability. You can still accept; the workshop checks again when you do.";
        tone = " inline-status-error";
    } else if (!capacity.withinWorkshopHours) {
        text = "Outside workshop hours. Pick a time between 06:00 and 20:00.";
        tone = " inline-status-error";
    } else if (capacity.available) {
        text = `Mechanic free at this time — ${capacity.remainingSlots} of ${capacity.technicianCount} available (${capacity.bookedCount} still on earlier open jobs).`;
        tone = " inline-status-ok";
    } else {
        text = `No mechanic free at this time (${capacity.bookedCount}/${capacity.technicianCount} busy on open jobs). Call the client to discuss another appointment.`;
        tone = " inline-status-error";
    }
    return (
        <p className={`inline-status${tone}`} role="status">
            {text}
        </p>
    );
}

/**
 * Workshop dashboard: consultants accept/schedule requests; technicians claim and complete work.
 */
export default function EmployeeDashboard() {
    const navigate = useNavigate();
    const session = getSession();
    const consultant = isConsultant(session?.role);
    const technician = isTechnician(session?.role);
    const sections = sectionLabels(consultant);
    const ledes = sectionLedes(consultant);

    const [section, setSection] = useState("available");
    const headingRef = useFocusOnChange(section);
    const [showPasswordForm, setShowPasswordForm] = useState(false);

    const [available, setAvailable] = useState([]);
    const [awaitingWorkshop, setAwaitingWorkshop] = useState([]);
    const [myBookings, setMyBookings] = useState([]);
    const [consultantArchive, setConsultantArchive] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [actionError, setActionError] = useState("");

    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [claimForms, setClaimForms] = useState({});
    const [rejectForms, setRejectForms] = useState({});
    const [acceptForms, setAcceptForms] = useState({});
    const [capacityByBookingId, setCapacityByBookingId] = useState({});
    const [completeForms, setCompleteForms] = useState({});
    const [cancelForms, setCancelForms] = useState({});
    const [workPlanForms, setWorkPlanForms] = useState({});
    const [workPlanMessage, setWorkPlanMessage] = useState({});
    const [archiveQuery, setArchiveQuery] = useState("");

    const assigned = useMemo(
        () => myBookings.filter((booking) => booking.status === "IN_PROGRESS"),
        [myBookings]
    );

    const assignmentList = consultant ? awaitingWorkshop : assigned;

    const archive = useMemo(() => {
        const list = consultant
            ? [...consultantArchive]
            : [...myBookings].filter((booking) => isArchiveStatus(booking.status));
        return list
            .filter((booking) => bookingMatchesQuery(booking, archiveQuery))
            .sort((a, b) => Number(b.id) - Number(a.id));
    }, [consultant, consultantArchive, myBookings, archiveQuery]);

    const sessionToken = session?.token;
    const sessionRole = session?.role;

    const loadBookings = useCallback(async () => {
        if (!sessionToken) return;
        setLoading(true);
        setError("");
        try {
            if (isConsultant(sessionRole)) {
                const [availableRes, awaitingRes, archiveRes] = await Promise.all([
                    api.get("/bookings/available"),
                    api.get("/workers/me/awaiting-workshop"),
                    api.get("/workers/me/consultant-archive"),
                ]);
                setAvailable(availableRes.data);
                setAwaitingWorkshop(awaitingRes.data);
                setConsultantArchive(archiveRes.data);
                setMyBookings([]);
            } else {
                const [availableRes, assignedRes] = await Promise.all([
                    api.get("/bookings/available"),
                    api.get("/workers/me/bookings"),
                ]);
                setAvailable(availableRes.data);
                setMyBookings(assignedRes.data);
                setAwaitingWorkshop([]);
                setConsultantArchive([]);
            }
        } catch {
            setError("Could not load bookings.");
        } finally {
            setLoading(false);
        }
    }, [sessionToken, sessionRole]);

    useEffect(() => {
        loadBookings();
    }, [loadBookings]);

    useEffect(() => {
        setAcceptForms((prev) => {
            let changed = false;
            const next = { ...prev };
            for (const booking of available) {
                if (
                    booking.status === "SCHEDULED"
                    && booking.estimatedDropOffTime
                    && !next[booking.id]?.scheduledDateTime
                ) {
                    changed = true;
                    next[booking.id] = {
                        ...next[booking.id],
                        scheduledDateTime: toDatetimeLocalValue(booking.estimatedDropOffTime),
                    };
                }
            }
            return changed ? next : prev;
        });
    }, [available]);

    // Live mechanic capacity at the appointment time the consultant is about to confirm.
    useEffect(() => {
        if (!consultant) return undefined;

        const controllers = [];
        const timers = [];

        for (const booking of available) {
            if (booking.status !== "SCHEDULED") continue;
            const localValue = acceptForms[booking.id]?.scheduledDateTime;
            if (!localValue || localValue.length < 16) {
                setCapacityByBookingId((prev) => {
                    if (!prev[booking.id]) return prev;
                    const next = { ...prev };
                    delete next[booking.id];
                    return next;
                });
                continue;
            }

            const at = `${localValue}:00`;
            const controller = new AbortController();
            controllers.push(controller);
            const timer = setTimeout(() => {
                api.get("/workers/me/workshop-capacity", {
                    params: { at },
                    signal: controller.signal,
                    skipAuthRedirect: true,
                })
                    .then((res) => {
                        setCapacityByBookingId((prev) => ({
                            ...prev,
                            [booking.id]: res.data,
                        }));
                    })
                    .catch((err) => {
                        if (err.code === "ERR_CANCELED" || err.name === "CanceledError") return;
                        setCapacityByBookingId((prev) => ({
                            ...prev,
                            [booking.id]: { error: true },
                        }));
                    });
            }, 250);
            timers.push(timer);
        }

        return () => {
            timers.forEach(clearTimeout);
            controllers.forEach((c) => c.abort());
        };
    }, [consultant, available, acceptForms]);

    useEffect(() => {
        setWorkPlanForms((prev) => {
            let changed = false;
            const next = { ...prev };
            for (const booking of assigned) {
                if (booking.status !== "IN_PROGRESS") continue;
                if (next[booking.id]?.touched) continue;
                changed = true;
                next[booking.id] = {
                    ...next[booking.id],
                    serviceTypes: (booking.serviceTypes ?? []).join(", "),
                    estimatedCost:
                        booking.estimatedCost != null ? String(booking.estimatedCost) : "",
                };
            }
            return changed ? next : prev;
        });
    }, [assigned]);

    const logout = () => {
        clearSession();
        navigate("/employee-login");
    };

    const selectSection = (next) => {
        setSection(next);
        setExpandedBookingId(null);
        setActionError("");
        if (next !== "account") setShowPasswordForm(false);
        if (next !== "archive") setArchiveQuery("");
    };

    const updateMap = (setter, id, field, value) => {
        setter((prev) => ({
            ...prev,
            [id]: { ...prev[id], [field]: value },
        }));
    };

    const updateWorkPlanField = (id, field, value) => {
        setWorkPlanForms((prev) => ({
            ...prev,
            [id]: { ...prev[id], [field]: value, touched: true },
        }));
    };

    const updateWorkPlan = async (bookingId) => {
        setActionError("");
        setWorkPlanMessage((prev) => ({ ...prev, [bookingId]: "" }));
        const form = workPlanForms[bookingId] ?? {};
        const serviceTypes = (form.serviceTypes ?? "")
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);
        if (serviceTypes.length === 0) {
            setWorkPlanMessage((prev) => ({
                ...prev,
                [bookingId]: "Enter at least one service for work planned.",
            }));
            return;
        }
        const costValue = form.estimatedCost?.toString().trim();
        const estimatedCost = costValue ? Number(costValue) : null;
        try {
            const res = await api.post(`/bookings/${bookingId}/work-plan`, {
                serviceTypes,
                estimatedCost: Number.isFinite(estimatedCost) ? estimatedCost : undefined,
            });
            const updated = res.data;
            setMyBookings((prev) =>
                prev.map((booking) => (booking.id === bookingId ? updated : booking))
            );
            setWorkPlanForms((prev) => ({
                ...prev,
                [bookingId]: {
                    serviceTypes: (updated.serviceTypes ?? []).join(", "),
                    estimatedCost:
                        updated.estimatedCost != null ? String(updated.estimatedCost) : "",
                    touched: false,
                },
            }));
            setWorkPlanMessage((prev) => ({
                ...prev,
                [bookingId]: WORK_PLAN_SAVED,
            }));
        } catch (err) {
            const message = err.response?.data?.message ?? "Could not update work planned.";
            setWorkPlanMessage((prev) => ({ ...prev, [bookingId]: message }));
            setActionError(message);
        }
    };

    const rejectBooking = async (bookingId) => {
        setActionError("");
        const reason = rejectForms[bookingId]?.reason?.trim();
        if (!reason) {
            setActionError("A reason is required to reject a booking.");
            return;
        }
        try {
            await api.post(`/bookings/${bookingId}/reject`, { reason });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not reject booking.");
        }
    };

    const cancelBooking = async (bookingId) => {
        setActionError("");
        const reason = cancelForms[bookingId]?.reason?.trim();
        if (!reason) {
            setActionError("A reason is required when cancelling a booking.");
            return;
        }
        try {
            await api.post(`/bookings/${bookingId}/cancel`, { reason });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not cancel booking.");
        }
    };

    const claimBooking = async (bookingId) => {
        setActionError("");
        const form = claimForms[bookingId] ?? {};
        const costValue = form.estimatedCost?.toString().trim();
        const estimatedCost = costValue ? Number(costValue) : null;
        try {
            await api.post(`/bookings/${bookingId}/claim`, {
                estimatedCost: Number.isFinite(estimatedCost) ? estimatedCost : null,
                serviceTypes: (form.serviceTypes ?? "")
                    .split(",")
                    .map((s) => s.trim())
                    .filter(Boolean),
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not claim booking.");
        }
    };

    const acceptBooking = async (bookingId) => {
        setActionError("");
        const form = acceptForms[bookingId] ?? {};
        const capacity = capacityByBookingId[bookingId];
        if (capacity && !capacity.error && !capacity.withinWorkshopHours) {
            setActionError("Pick an appointment time between 06:00 and 20:00.");
            return;
        }
        if (capacity && !capacity.error && !capacity.available) {
            setActionError(
                "No mechanic free at that time. Call the client to agree another appointment."
            );
            return;
        }
        try {
            await api.post(`/bookings/${bookingId}/accept`, {
                scheduledDateTime: form.scheduledDateTime
                    ? `${form.scheduledDateTime}:00`
                    : undefined,
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not accept booking.");
        }
    };

    const completeBooking = async (bookingId) => {
        setActionError("");
        const form = completeForms[bookingId] ?? {};
        try {
            await api.post(`/bookings/${bookingId}/complete`, {
                finalCost: Number(form.finalCost),
            });
            loadBookings();
        } catch (err) {
            setActionError(err.response?.data?.message ?? "Could not complete booking.");
        }
    };

    const submitWith = (action) => (e) => {
        e.preventDefault();
        action();
    };

    const renderBookingDetails = (booking) => (
        <>
            <DetailList
                items={[
                    { label: "Customer", value: booking.customerName || null },
                    { label: "VIN", value: bookingVin(booking) },
                    { label: "Issue", value: booking.customerDescription || null },
                    {
                        label: "Work planned",
                        value: booking.serviceTypes?.length
                            ? booking.serviceTypes.join(", ")
                            : null,
                    },
                    {
                        label: "Estimated drop-off",
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
                    { label: "Estimated cost", value: formatEuro(booking.estimatedCost) },
                    { label: "Final cost", value: formatEuro(booking.finalCost) },
                    { label: "Technician", value: assignedWorkerName(booking) },
                    { label: "Availability notes", value: booking.availabilityNotes || null },
                    {
                        label: "Cancelled by",
                        value: cancelledByLabel(booking.cancelledBy),
                        hidden: booking.status !== "CANCELLED",
                    },
                    {
                        label: "Cancellation reason",
                        value: booking.cancellationReason?.trim() || null,
                        hidden: booking.status !== "CANCELLED",
                    },
                ]}
            />
            {!hasCustomerAvailability(booking) && (
                <p className="inline-status">No preferred date provided by customer.</p>
            )}
        </>
    );

    const reasonForm = ({ booking, forms, setter, label, buttonLabel, onSubmit }) => {
        const hintId = `${label.toLowerCase().replaceAll(" ", "-")}-hint-${booking.id}`;
        return (
            <form className="row-section inline-action" onSubmit={submitWith(onSubmit)}>
                <label className="field">
                    <span className="field-label">{label}</span>
                    <input
                        className="input"
                        aria-describedby={hintId}
                        value={forms[booking.id]?.reason ?? ""}
                        onChange={(e) => updateMap(setter, booking.id, "reason", e.target.value)}
                    />
                    <span id={hintId} className="field-hint">
                        Required. The client sees this reason.
                    </span>
                </label>
                <button type="submit" className="button button-danger">
                    {buttonLabel}
                </button>
            </form>
        );
    };

    const renderAvailableActions = (booking) => (
        <>
            {consultant && (
                <>
                    <form
                        className="row-section action-group"
                        onSubmit={submitWith(() => acceptBooking(booking.id))}
                    >
                        <label className="field field-narrow">
                            <span className="field-label">Appointment</span>
                            <input
                                className="input"
                                type="datetime-local"
                                value={acceptForms[booking.id]?.scheduledDateTime ?? ""}
                                onChange={(e) =>
                                    updateMap(
                                        setAcceptForms,
                                        booking.id,
                                        "scheduledDateTime",
                                        e.target.value
                                    )
                                }
                            />
                        </label>
                        <CapacityStatus capacity={capacityByBookingId[booking.id]} />
                        <div className="button-row">
                            <button
                                type="submit"
                                className="button button-primary"
                                disabled={!canAccept(
                                    acceptForms[booking.id],
                                    capacityByBookingId[booking.id]
                                )}
                            >
                                Accept booking
                            </button>
                        </div>
                    </form>
                    {reasonForm({
                        booking,
                        forms: rejectForms,
                        setter: setRejectForms,
                        label: "Rejection reason",
                        buttonLabel: "Reject",
                        onSubmit: () => rejectBooking(booking.id),
                    })}
                </>
            )}

            {technician && (
                <form
                    className="row-section action-group"
                    onSubmit={submitWith(() => claimBooking(booking.id))}
                >
                    <div className="form-grid">
                        <label className="field">
                            <span className="field-label">Estimated cost (EUR)</span>
                            <input
                                className="input"
                                type="number"
                                inputMode="decimal"
                                min="0"
                                step="0.01"
                                placeholder="Optional"
                                value={claimForms[booking.id]?.estimatedCost ?? ""}
                                onChange={(e) =>
                                    updateMap(setClaimForms, booking.id, "estimatedCost", e.target.value)
                                }
                            />
                        </label>
                        <label className="field">
                            <span className="field-label">Services</span>
                            <input
                                className="input"
                                placeholder="e.g. car check"
                                aria-describedby={`claim-services-hint-${booking.id}`}
                                value={claimForms[booking.id]?.serviceTypes ?? ""}
                                onChange={(e) =>
                                    updateMap(setClaimForms, booking.id, "serviceTypes", e.target.value)
                                }
                            />
                            <span id={`claim-services-hint-${booking.id}`} className="field-hint">
                                Separate services with commas.
                            </span>
                        </label>
                    </div>
                    <div className="button-row">
                        <button type="submit" className="button button-primary">
                            Claim job
                        </button>
                    </div>
                </form>
            )}
        </>
    );

    const renderAssignmentActions = (booking) => (
        <>
            {consultant && booking.status === "READY_FOR_WORK" && reasonForm({
                booking,
                forms: cancelForms,
                setter: setCancelForms,
                label: "Cancellation reason",
                buttonLabel: "Cancel booking",
                onSubmit: () => cancelBooking(booking.id),
            })}

            {technician && booking.status === "IN_PROGRESS" && (
                <>
                    <form
                        className="row-section action-group"
                        onSubmit={submitWith(() => updateWorkPlan(booking.id))}
                    >
                        <div className="form-grid">
                            <label className="field">
                                <span className="field-label">Work planned</span>
                                <input
                                    className="input"
                                    placeholder="e.g. brake replace"
                                    aria-describedby={`plan-hint-${booking.id}`}
                                    value={
                                        workPlanForms[booking.id]?.serviceTypes
                                        ?? (booking.serviceTypes ?? []).join(", ")
                                    }
                                    onChange={(e) =>
                                        updateWorkPlanField(booking.id, "serviceTypes", e.target.value)
                                    }
                                />
                                <span id={`plan-hint-${booking.id}`} className="field-hint">
                                    Separate services with commas.
                                </span>
                            </label>
                            <label className="field">
                                <span className="field-label">Estimated cost (EUR)</span>
                                <input
                                    className="input"
                                    type="number"
                                    inputMode="decimal"
                                    min="0"
                                    step="0.01"
                                    value={
                                        workPlanForms[booking.id]?.estimatedCost
                                        ?? (booking.estimatedCost != null
                                            ? String(booking.estimatedCost)
                                            : "")
                                    }
                                    onChange={(e) =>
                                        updateWorkPlanField(booking.id, "estimatedCost", e.target.value)
                                    }
                                />
                            </label>
                        </div>
                        <div className="button-row">
                            <button type="submit" className="button button-secondary">
                                Update work planned
                            </button>
                        </div>
                        <div role="status">
                            {workPlanMessage[booking.id] && (
                                <p
                                    className={`inline-status ${
                                        workPlanMessage[booking.id] === WORK_PLAN_SAVED
                                            ? "inline-status-ok"
                                            : "inline-status-error"
                                    }`}
                                >
                                    {workPlanMessage[booking.id]}
                                </p>
                            )}
                        </div>
                    </form>

                    <form
                        className="row-section inline-action"
                        onSubmit={submitWith(() => completeBooking(booking.id))}
                    >
                        <label className="field">
                            <span className="field-label">Final cost (EUR)</span>
                            <input
                                className="input"
                                type="number"
                                inputMode="decimal"
                                min="0"
                                step="0.01"
                                aria-describedby={`final-hint-${booking.id}`}
                                value={completeForms[booking.id]?.finalCost ?? ""}
                                onChange={(e) =>
                                    updateMap(setCompleteForms, booking.id, "finalCost", e.target.value)
                                }
                            />
                            <span id={`final-hint-${booking.id}`} className="field-hint">
                                Required to complete. Appears on the invoice.
                            </span>
                        </label>
                        <button type="submit" className="button button-primary">
                            Complete
                        </button>
                    </form>

                    {reasonForm({
                        booking,
                        forms: cancelForms,
                        setter: setCancelForms,
                        label: "Cancellation reason",
                        buttonLabel: "Cancel booking",
                        onSubmit: () => cancelBooking(booking.id),
                    })}
                </>
            )}
        </>
    );

    const renderRow = (booking, prefix, meta, actions) => {
        const key = `${prefix}-${booking.id}`;
        return (
            <ExpandableRow
                key={booking.id}
                eyebrow={`Booking #${booking.id}`}
                title={booking.carModel}
                meta={meta}
                badge={<StatusBadge status={booking.status} />}
                highlight={booking.status === "IN_PROGRESS" && prefix !== "archive"}
                expanded={expandedBookingId === key}
                onToggle={() =>
                    setExpandedBookingId((current) => (current === key ? null : key))
                }
            >
                {renderBookingDetails(booking)}
                {actions}
            </ExpandableRow>
        );
    };

    const lists = {
        available: {
            items: available,
            empty: consultant ? "No new requests." : "No jobs waiting to be claimed.",
            render: (booking) =>
                renderRow(
                    booking,
                    "available",
                    joinMeta([
                        bookingVin(booking) && `VIN ${bookingVin(booking)}`,
                        booking.customerName,
                        booking.estimatedDropOffTime
                            && `Drop-off ${formatCustomerDateTime(booking.estimatedDropOffTime)}`,
                        booking.scheduledDateTime
                            && `Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`,
                    ]),
                    renderAvailableActions(booking)
                ),
        },
        assignments: {
            items: assignmentList,
            empty: consultant ? "Nothing in the workshop right now." : "You have no active jobs.",
            render: (booking) =>
                renderRow(
                    booking,
                    "assigned",
                    joinMeta([
                        bookingVin(booking) && `VIN ${bookingVin(booking)}`,
                        booking.customerName,
                        booking.scheduledDateTime
                            ? `Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`
                            : booking.estimatedDropOffTime
                                && `Preferred ${formatCustomerDateTime(booking.estimatedDropOffTime)}`,
                        consultant && assignedWorkerName(booking),
                    ]),
                    renderAssignmentActions(booking)
                ),
        },
        archive: {
            items: archive,
            empty: archiveQuery ? `No bookings match “${archiveQuery}”.` : "No archived bookings yet.",
            render: (booking) =>
                renderRow(
                    booking,
                    "archive",
                    joinMeta([
                        bookingVin(booking) && `VIN ${bookingVin(booking)}`,
                        booking.customerName,
                        booking.scheduledDateTime
                            && `Appointment ${formatCustomerDateTime(booking.scheduledDateTime)}`,
                        assignedWorkerName(booking),
                        booking.status === "CANCELLED"
                            && cancelledByLabel(booking.cancelledBy)
                            && `Cancelled by ${cancelledByLabel(booking.cancelledBy)}`,
                        booking.status === "COMPLETED"
                            && booking.finalCost != null
                            && `${booking.finalCost} EUR`,
                    ]),
                    null
                ),
        },
    };

    const list = lists[section];

    return (
        <AppShell
            pageTitle={sections[section]}
            title={consultant ? "Service desk" : "Workshop"}
            homeTo="/employee"
            userName={session?.name}
            onSignOut={logout}
            sections={sections}
            activeSection={section}
            onSelectSection={selectSection}
        >
            <header className="page-header">
                <h1 ref={headingRef} tabIndex={-1} className="page-title">
                    {sections[section]}
                </h1>
                <p className="page-lede">{ledes[section]}</p>
            </header>

            <div className="stack">
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
                {actionError && (
                    <p className="message message-error" role="alert">
                        {actionError}
                    </p>
                )}

                {section === "archive" && (
                    <div className="toolbar">
                        <label className="field">
                            <span className="field-label">Search archive</span>
                            <input
                                className="input"
                                type="search"
                                placeholder="Customer, VIN, model, status"
                                value={archiveQuery}
                                onChange={(e) => setArchiveQuery(e.target.value)}
                            />
                        </label>
                    </div>
                )}

                {list && !loading && list.items.length === 0 && (
                    <p className="empty-state" role={section === "archive" ? "status" : undefined}>
                        {list.empty}
                    </p>
                )}
                {list && list.items.length > 0 && (
                    <ul className="row-list">{list.items.map(list.render)}</ul>
                )}

                {section === "account" && (
                    <PasswordCard open={showPasswordForm} onOpenChange={setShowPasswordForm} />
                )}
            </div>
        </AppShell>
    );
}
