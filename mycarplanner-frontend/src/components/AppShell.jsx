import { usePageTitle } from "../hooks/usePageTitle";
import GlobalNav from "./GlobalNav";
import LocalNav from "./LocalNav";
import SiteFooter from "./SiteFooter";
import SkipLink from "./SkipLink";
import "./Shell.css";

/** Signed-in page frame: global nav, local nav, main landmark, footer. */
export default function AppShell({
    pageTitle,
    title,
    homeTo,
    userName,
    onSignOut,
    sections,
    activeSection,
    onSelectSection,
    localAction,
    children,
}) {
    usePageTitle(pageTitle);

    return (
        <>
            <SkipLink />
            <GlobalNav
                homeTo={homeTo}
                actions={
                    <>
                        {userName && (
                            <span className="global-nav-user">
                                <span className="visually-hidden">Signed in as </span>
                                {userName}
                            </span>
                        )}
                        {onSignOut && (
                            <button type="button" className="global-nav-action" onClick={onSignOut}>
                                Sign out
                            </button>
                        )}
                    </>
                }
            />
            <LocalNav
                title={title}
                sections={sections}
                activeSection={activeSection}
                onSelect={onSelectSection}
                action={localAction}
            />
            <main id="main" tabIndex={-1} className="app-main">
                <div className="container">{children}</div>
            </main>
            <SiteFooter showLinks={false} />
        </>
    );
}
