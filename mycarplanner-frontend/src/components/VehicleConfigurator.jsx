import { useEffect, useMemo, useState } from "react";
import { carImageProps } from "../constants/models";
import { useFocusOnChange } from "../hooks/useFocusOnChange";
import { api } from "../services/api";
import "./VehicleConfigurator.css";

const MODEL_IMAGES = {
    "hussarya-thunder": "thunder",
    "hussarya-bastion": "bastion",
    "hussarya-zefir": "zefir",
};

function modelImage(modelLineId) {
    return MODEL_IMAGES[modelLineId] ?? null;
}

function uniqueBy(items, keyFn) {
    const seen = new Set();
    return items.filter((item) => {
        const key = keyFn(item);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

/**
 * Step-by-step picker over GET /vehicles/catalog: model, body style, engine, transmission,
 * year, then VIN. Earlier choices stay visible as a breadcrumb; Back undoes the last one.
 */
export default function VehicleConfigurator({ onVehicleAdded }) {
    const [catalog, setCatalog] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const [step, setStep] = useState(0);
    const [modelQuery, setModelQuery] = useState("");

    const [modelLineId, setModelLineId] = useState("");
    const [bodyStyleId, setBodyStyleId] = useState("");
    const [engine, setEngine] = useState("");
    const [configurationId, setConfigurationId] = useState("");
    const [productionYear, setProductionYear] = useState("");
    const [vin, setVin] = useState("");
    const [vinTaken, setVinTaken] = useState(false);
    const [vinChecking, setVinChecking] = useState(false);


    useEffect(() => {
        api.get("/vehicles/catalog")
            .then((res) => setCatalog(res.data))
            .catch(() => setError("Could not load Hussarya catalog."))
            .finally(() => setLoading(false));
    }, []);

    const modelLine = useMemo(
        () => catalog?.modelLines?.find((line) => line.id === modelLineId) ?? null,
        [catalog, modelLineId]
    );

    const bodyStyle = useMemo(
        () => modelLine?.bodyStyles?.find((style) => style.id === bodyStyleId) ?? null,
        [modelLine, bodyStyleId]
    );

    const configurations = bodyStyle?.configurations ?? [];

    const engineOptions = useMemo(
        () => uniqueBy(configurations, (config) => config.engine),
        [configurations]
    );

    const transmissionOptions = useMemo(
        () => configurations.filter((config) => config.engine === engine),
        [configurations, engine]
    );

    const selectedConfiguration = useMemo(
        () => configurations.find((config) => config.id === configurationId) ?? null,
        [configurations, configurationId]
    );

    const hasYearStep = (modelLine?.availableYears?.length ?? 0) > 0;

    const steps = useMemo(() => {
        const list = ["model", "body", "engine", "transmission"];
        if (hasYearStep) list.push("year");
        list.push("vin");
        return list;
    }, [hasYearStep]);

    const currentStepId = steps[step] ?? "model";
    const stepNumber = step + 1;
    const totalSteps = steps.length;
    const stepHeadingRef = useFocusOnChange(currentStepId);

    // Check VIN availability while typing so a conflict shows before submit.
    useEffect(() => {
        const normalized = vin.trim().replace(/\s+/g, "").toUpperCase();
        if (!normalized || currentStepId !== "vin") {
            setVinTaken(false);
            setVinChecking(false);
            return undefined;
        }

        let cancelled = false;
        setVinChecking(true);
        const timer = setTimeout(() => {
            api.get("/vehicles/me/vin-available", {
                params: { vin: normalized },
                skipAuthRedirect: true,
            })
                .then((res) => {
                    if (cancelled) return;
                    const available = res.data?.available !== false;
                    setVinTaken(!available);
                    if (available) {
                        setError((prev) =>
                            prev === "This VIN is already registered to a vehicle" ? "" : prev
                        );
                    }
                })
                .catch(() => {
                    if (!cancelled) setVinTaken(false);
                })
                .finally(() => {
                    if (!cancelled) setVinChecking(false);
                });
        }, 350);

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [vin, currentStepId]);

    const filteredModels = useMemo(() => {
        const lines = catalog?.modelLines ?? [];
        const q = modelQuery.trim().toLowerCase();
        if (!q) return lines;
        return lines.filter(
            (line) =>
                line.name?.toLowerCase().includes(q)
                || line.era?.toLowerCase().includes(q)
        );
    }, [catalog, modelQuery]);

    const trail = useMemo(() => {
        const items = [];
        if (modelLine) items.push({ label: modelLine.name, stepId: "model" });
        if (bodyStyle) items.push({ label: bodyStyle.name, stepId: "body" });
        if (engine) items.push({ label: engine, stepId: "engine" });
        if (selectedConfiguration) {
            items.push({
                label: selectedConfiguration.transmission,
                stepId: "transmission",
            });
        }
        if (productionYear) {
            items.push({ label: String(productionYear), stepId: "year" });
        }
        return items;
    }, [modelLine, bodyStyle, engine, selectedConfiguration, productionYear]);

    const clearAfter = (fromStepId) => {
        if (fromStepId === "model") {
            setBodyStyleId("");
            setEngine("");
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "body") {
            setEngine("");
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "engine") {
            setConfigurationId("");
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "transmission") {
            setProductionYear("");
            setVin("");
        } else if (fromStepId === "year") {
            setVin("");
        }
        setError("");
        setSuccess("");
    };

    const jumpToStep = (stepId) => {
        const index = steps.indexOf(stepId);
        if (index < 0 || index >= step) return;
        clearAfter(stepId);
        setStep(index);
    };

    const canContinue = () => {
        switch (currentStepId) {
            case "model":
                return Boolean(modelLineId);
            case "body":
                return Boolean(bodyStyleId);
            case "engine":
                return Boolean(engine);
            case "transmission":
                return Boolean(configurationId);
            case "year":
                return Boolean(productionYear);
            case "vin":
                return Boolean(vin.trim()) && !vinTaken && !vinChecking && Boolean(configurationId) && (
                    !hasYearStep || Boolean(productionYear)
                );
            default:
                return false;
        }
    };

    const resolvedProductionYear = () => {
        if (productionYear) return Number(productionYear);
        const years = modelLine?.availableYears;
        if (years?.length) return Number(years[years.length - 1]);
        return null;
    };

    const goBack = () => {
        if (step === 0) return;
        if (currentStepId === "body") setBodyStyleId("");
        if (currentStepId === "engine") setEngine("");
        if (currentStepId === "transmission") setConfigurationId("");
        if (currentStepId === "year") setProductionYear("");
        if (currentStepId === "vin") setVin("");
        clearAfter(steps[step - 1]);
        setStep((s) => Math.max(0, s - 1));
    };

    const goContinue = () => {
        if (!canContinue()) return;
        if (currentStepId === "vin") {
            submitVehicle();
            return;
        }
        setStep((s) => Math.min(totalSteps - 1, s + 1));
        setError("");
        setSuccess("");
    };

    const submitVehicle = async () => {
        const year = resolvedProductionYear();
        const normalizedVin = vin.trim().replace(/\s+/g, "").toUpperCase();
        if (!configurationId || year == null || !normalizedVin) {
            setError("Select your car specification, production year, and enter the VIN.");
            return;
        }
        if (vinTaken) {
            setError("This VIN is already registered to a vehicle");
            return;
        }

        setSubmitting(true);
        setError("");
        setSuccess("");

        try {
            await api.post("/vehicles/me", {
                configurationId,
                productionYear: year,
                vin: normalizedVin,
            });
            setSuccess("Vehicle added to your account.");
            onVehicleAdded?.();
        } catch (err) {
            const message = err.response?.data?.message ?? "Failed to add vehicle.";
            if (err.response?.status === 409) {
                setVinTaken(true);
                setError("");
            } else {
                setError(message);
            }
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <p className="status-text" role="status">
                Loading Hussarya catalog…
            </p>
        );
    }

    if (!catalog) {
        return (
            <p className="message message-error" role="alert">
                {error || "Catalog unavailable."}
            </p>
        );
    }

    const titles = {
        model: "Choose your model.",
        body: "Choose body style.",
        engine: "Choose engine.",
        transmission: "Choose transmission.",
        year: "Choose production year.",
        vin: "Enter the VIN.",
    };

    const optionClass = (selected, extra = "") =>
        ["option-btn", extra, selected ? "selected" : ""].filter(Boolean).join(" ");

    return (
        <div className="vehicle-configurator">
            <div className="config-progress" aria-hidden="true">
                <span style={{ width: `${(stepNumber / totalSteps) * 100}%` }} />
            </div>

            {trail.length > 0 && (
                <nav aria-label="Your selections so far">
                    <ol className="config-trail">
                        {trail.map((item) => (
                            <li key={`${item.stepId}-${item.label}`}>
                                <button
                                    type="button"
                                    className="config-trail-btn"
                                    onClick={() => jumpToStep(item.stepId)}
                                >
                                    <span className="visually-hidden">Change </span>
                                    {item.label}
                                </button>
                            </li>
                        ))}
                    </ol>
                </nav>
            )}

            <section className="config-step-panel" key={currentStepId} aria-labelledby="config-step-title">
                <div className="config-step-meta">
                    <p className="config-step-count">
                        Step {stepNumber} of {totalSteps}
                    </p>
                    <h2 id="config-step-title" ref={stepHeadingRef} tabIndex={-1} className="config-step-title">
                        {titles[currentStepId]}
                    </h2>
                </div>

                {currentStepId === "model" && (
                    <>
                        <label className="field model-search">
                            <span className="field-label">Search models</span>
                            <input
                                className="input"
                                type="search"
                                value={modelQuery}
                                onChange={(e) => setModelQuery(e.target.value)}
                            />
                        </label>
                        {filteredModels.length === 0 && (
                            <p className="empty-state" role="status">
                                No models match “{modelQuery}”.
                            </p>
                        )}
                        <div className="option-grid option-grid-models">
                            {filteredModels.map((line) => {
                                const image = modelImage(line.id);
                                return (
                                    <button
                                        key={line.id}
                                        type="button"
                                        aria-pressed={modelLineId === line.id}
                                        className={optionClass(modelLineId === line.id, "option-model")}
                                        onClick={() => {
                                            setModelLineId(line.id);
                                            clearAfter("model");
                                        }}
                                    >
                                        {image && (
                                            <span className="option-model-image studio">
                                                <img {...carImageProps(image, "(max-width: 833px) 100vw, 320px")} alt="" loading="lazy" />
                                            </span>
                                        )}
                                        <span className="option-label">{line.name}</span>
                                        <span className="option-sub">{line.era}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </>
                )}

                {currentStepId === "body" && modelLine && (
                    <div className="option-grid">
                        {modelLine.bodyStyles.map((style) => (
                            <button
                                key={style.id}
                                type="button"
                                aria-pressed={bodyStyleId === style.id}
                                className={optionClass(bodyStyleId === style.id)}
                                onClick={() => {
                                    setBodyStyleId(style.id);
                                    clearAfter("body");
                                }}
                            >
                                <span className="option-label">{style.name}</span>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "engine" && (
                    <div className="option-grid">
                        {engineOptions.map((config) => (
                            <button
                                key={config.engine}
                                type="button"
                                aria-pressed={engine === config.engine}
                                className={optionClass(engine === config.engine)}
                                onClick={() => {
                                    setEngine(config.engine);
                                    clearAfter("engine");
                                }}
                            >
                                <span className="option-label">{config.engine}</span>
                                <span className="option-sub">{config.power}</span>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "transmission" && (
                    <div className="option-grid">
                        {transmissionOptions.map((config) => (
                            <button
                                key={config.id}
                                type="button"
                                aria-pressed={configurationId === config.id}
                                className={optionClass(configurationId === config.id)}
                                onClick={() => {
                                    setConfigurationId(config.id);
                                    clearAfter("transmission");
                                }}
                            >
                                <span className="option-label">{config.transmission}</span>
                                <span className="option-sub">{config.drivetrain}</span>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "year" && modelLine && (
                    <div className="option-grid option-grid-compact">
                        {modelLine.availableYears.map((year) => (
                            <button
                                key={year}
                                type="button"
                                aria-pressed={productionYear === year}
                                className={optionClass(productionYear === year)}
                                onClick={() => {
                                    setProductionYear(year);
                                    clearAfter("year");
                                }}
                            >
                                <span className="option-label">{year}</span>
                            </button>
                        ))}
                    </div>
                )}

                {currentStepId === "vin" && (
                    <div className="vin-step">
                        {selectedConfiguration && (
                            <div className="config-summary">
                                <p className="config-summary-title">{selectedConfiguration.displayName}</p>
                                <dl className="detail-list">
                                    {productionYear && (
                                        <div className="detail-item">
                                            <dt>Production year</dt>
                                            <dd>{productionYear}</dd>
                                        </div>
                                    )}
                                    <div className="detail-item">
                                        <dt>Engine</dt>
                                        <dd>
                                            {selectedConfiguration.engine} ({selectedConfiguration.power})
                                        </dd>
                                    </div>
                                    <div className="detail-item">
                                        <dt>Transmission</dt>
                                        <dd>{selectedConfiguration.transmission}</dd>
                                    </div>
                                    <div className="detail-item">
                                        <dt>Drivetrain</dt>
                                        <dd>{selectedConfiguration.drivetrain}</dd>
                                    </div>
                                </dl>
                            </div>
                        )}
                        <label className="field">
                            <span className="field-label">Vehicle identification number (VIN)</span>
                            <input
                                className="input vin-input"
                                value={vin}
                                autoCapitalize="characters"
                                autoComplete="off"
                                spellCheck={false}
                                aria-invalid={vinTaken || undefined}
                                aria-describedby="vin-status"
                                onChange={(e) => {
                                    const next = e.target.value.replace(/\s+/g, "").toUpperCase();
                                    setVin(next);
                                    setVinTaken(false);
                                    setError("");
                                    setSuccess("");
                                }}
                            />
                        </label>
                        <div id="vin-status" role="status">
                            {vinChecking && vin.trim() && (
                                <p className="field-hint">Checking VIN…</p>
                            )}
                            {vinTaken && (
                                <p className="field-error">This VIN is already registered to a vehicle</p>
                            )}
                        </div>
                    </div>
                )}

                <div className="button-row config-actions">
                    <button
                        type="button"
                        className="button button-primary"
                        disabled={submitting || !canContinue()}
                        onClick={goContinue}
                    >
                        {currentStepId === "vin"
                            ? (submitting ? "Adding vehicle…" : "Add vehicle")
                            : "Continue"}
                    </button>
                    {step > 0 && (
                        <button
                            type="button"
                            className="link-chevron link-chevron-back"
                            onClick={goBack}
                            disabled={submitting}
                        >
                            Back
                        </button>
                    )}
                </div>

                {error && (
                    <p className="message message-error" role="alert">
                        {error}
                    </p>
                )}
                {success && (
                    <p className="message message-success" role="status">
                        {success}
                    </p>
                )}
            </section>
        </div>
    );
}
