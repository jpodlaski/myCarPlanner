import { useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import GlobalNav from "../components/GlobalNav";
import { PUBLIC_LINKS } from "../constants/navigation";
import SiteFooter from "../components/SiteFooter";
import SkipLink from "../components/SkipLink";
import { HUSSARYA_MODELS, carImageProps } from "../constants/models";
import { WORKSHOP_ADDRESS } from "../constants/workshop";
import { usePageTitle } from "../hooks/usePageTitle";
import "../components/Shell.css";
import "./Landing.css";

const STEPS = [
    {
        title: "Add your Hussarya.",
        text: "Pick your model, engine and year, then add the VIN. It takes under a minute.",
    },
    {
        title: "Request service.",
        text: "Describe the issue and choose a drop-off slot between 06:00 and 20:00.",
    },
    {
        title: "Follow it to the invoice.",
        text: "See when the workshop confirms, starts and completes the work, then download the invoice.",
    },
];

const MAPS_URL = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(WORKSHOP_ADDRESS)}`;

export default function Landing() {
    usePageTitle(null);
    const { hash } = useLocation();

    useEffect(() => {
        if (!hash) return;
        document.getElementById(hash.slice(1))?.scrollIntoView();
    }, [hash]);

    return (
        <>
            <SkipLink />
            <GlobalNav
                links={PUBLIC_LINKS}
                actions={
                    <Link to="/login" className="global-nav-action">
                        Sign in
                    </Link>
                }
            />

            <main id="main" tabIndex={-1} className="landing-main">
                <section className="hero" aria-labelledby="hero-title">
                    <div className="container hero-copy">
                        <p className="hero-eyebrow">myCarPlanner</p>
                        <h1 id="hero-title" className="hero-title">
                            Hussarya Service.
                        </h1>
                        <p className="hero-subtitle">
                            Book care for your car in minutes. Follow it from drop-off to invoice.
                        </p>
                        <div className="hero-ctas">
                            <Link to="/register" className="button button-primary">
                                Create account
                            </Link>
                            <Link to="/login" className="link-chevron">
                                Sign in
                            </Link>
                        </div>
                    </div>
                    <div className="hero-figure studio">
                        <img
                            {...carImageProps("thunder", "(max-width: 1200px) 100vw, 1200px")}
                            alt="Hussarya Thunder supercar in dark graphite, seen from the front"
                            fetchPriority="high"
                        />
                    </div>
                </section>

                <section id="service" className="band band-alt" aria-labelledby="service-title">
                    <div className="container container-wide">
                        <h2 id="service-title" className="band-title">
                            Service, simplified.
                        </h2>
                        <p className="band-lede">
                            Three steps from your sofa to the workshop floor.
                        </p>
                        <ol className="steps">
                            {STEPS.map((step, index) => (
                                <li key={step.title} className="step-tile">
                                    <span className="step-number" aria-hidden="true">
                                        {index + 1}
                                    </span>
                                    <h3 className="step-title">{step.title}</h3>
                                    <p className="step-text">{step.text}</p>
                                </li>
                            ))}
                        </ol>
                    </div>
                </section>

                <section id="models" className="band" aria-labelledby="models-title">
                    <div className="container container-wide">
                        <h2 id="models-title" className="band-title">
                            Every Hussarya. Looked after.
                        </h2>
                        <p className="band-lede">
                            Thunder, Bastion and Zefir, serviced in one place.
                        </p>
                        <ul className="model-grid">
                            {HUSSARYA_MODELS.map((model) => (
                                <li key={model.id} className="model-tile">
                                    <div className="model-image studio">
                                        <img
                                            {...carImageProps(model.image)}
                                            alt={model.alt}
                                            loading="lazy"
                                        />
                                    </div>
                                    <div className="model-copy">
                                        <p className="model-eyebrow">
                                            {model.body} · since {model.since}
                                        </p>
                                        <h3 className="model-name">{model.name}</h3>
                                        <p className="model-spec">{model.engines}</p>
                                        <p className="model-spec">{model.drivetrain}</p>
                                        <Link to="/register" className="link-chevron model-link">
                                            Book service
                                            <span className="visually-hidden"> for your {model.name}</span>
                                        </Link>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    </div>
                </section>

                <section id="location" className="band band-alt" aria-labelledby="location-title">
                    <div className="container container-wide location-grid">
                        <div className="location-tile">
                            <h2 id="location-title" className="band-title band-title-left">
                                Visit the workshop.
                            </h2>
                            <address className="location-address">{WORKSHOP_ADDRESS}</address>
                            <p className="location-hours">Drop-off daily, 06:00–20:00</p>
                            <a href={MAPS_URL} className="link-chevron">
                                Open in Google Maps
                            </a>
                        </div>
                        <div className="location-tile location-tile-staff">
                            <h3 className="location-staff-title">Working at Hussarya Service?</h3>
                            <p className="location-staff-text">
                                Consultants, technicians and management sign in separately.
                            </p>
                            <Link to="/employee-login" className="link-chevron">
                                Staff sign in
                            </Link>
                        </div>
                    </div>
                </section>

                <section className="band closing" aria-labelledby="closing-title">
                    <div className="container">
                        <h2 id="closing-title" className="band-title">
                            Ready when your car is.
                        </h2>
                        <div className="hero-ctas">
                            <Link to="/register" className="button button-primary">
                                Create account
                            </Link>
                            <Link to="/login" className="link-chevron">
                                Sign in
                            </Link>
                        </div>
                    </div>
                </section>
            </main>

            <SiteFooter />
        </>
    );
}
