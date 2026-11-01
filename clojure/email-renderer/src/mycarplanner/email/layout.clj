(ns mycarplanner.email.layout
  "Shared branding and document shell for myCarPlanner notification emails."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.time LocalDate]
           [java.util Base64]))

(def workshop-address "Sportowa 31, Łódź, Poland")

(def brand-name "myCarPlanner")

(def workshop-name "Hussarya Service")

(def workshop-hours "06:00–20:00")

;; Matches the light theme tokens in mycarplanner-frontend/src/index.css.
;; Email clients ignore CSS variables, so every value is inlined.
(def ^:private page-bg "#f5f5f7")
(def ^:private card-bg "#ffffff")
(def ^:private inset-bg "#f5f5f7")
(def ^:private text-primary "#1d1d1f")
(def ^:private text-secondary "#6e6e73")
(def ^:private separator "#d2d2d7")
(def ^:private accent "#0071e3")
(def ^:private link-color "#0066cc")
(def ^:private font-stack
  "-apple-system,BlinkMacSystemFont,'SF Pro Text','Helvetica Neue',Helvetica,Arial,sans-serif")
(def ^:private display-font-stack
  "-apple-system,BlinkMacSystemFont,'SF Pro Display','Helvetica Neue',Helvetica,Arial,sans-serif")

;; Clients that support <style> (Apple Mail, iOS Mail, Outlook.com) get dark mode
;; and a tighter mobile layout; the rest fall back to the inline light styles.
(def ^:private head-styles
  (str ":root{color-scheme:light dark;supported-color-schemes:light dark;}"
       "body{margin:0;padding:0;-webkit-text-size-adjust:100%;}"
       "a{color:" link-color ";}"
       "@media (max-width:620px){"
       ".mcp-card{padding:32px 24px!important;border-radius:0!important;}"
       ".mcp-shell{padding:24px 0 32px!important;}"
       ".mcp-headline{font-size:28px!important;line-height:1.14!important;}"
       "}"
       "@media (prefers-color-scheme:dark){"
       ".mcp-page{background-color:#000000!important;}"
       ".mcp-card{background-color:#1d1d1f!important;}"
       ".mcp-inset{background-color:#2c2c2e!important;}"
       ".mcp-text{color:#f5f5f7!important;}"
       ".mcp-muted{color:#a1a1a6!important;}"
       ".mcp-link{color:#2997ff!important;}"
       ".mcp-rule{border-color:#424245!important;}"
       "}"))

(defn- logo-bytes []
  (with-open [in (io/input-stream (io/resource "email/logo.png"))]
    (.readAllBytes in)))

(def ^:private logo-data-uri
  (memoize
   (fn []
     (str "data:image/png;base64,"
          (.encodeToString (Base64/getEncoder) (logo-bytes))))))

(defn escape-html [value]
  (-> (str (or value ""))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- blank-line? [x]
  (or (nil? x) (str/blank? (str x))))
(defn- keep-non-blank-lines [lines acc]
  (if (empty? lines)
    (reverse acc)
    (let [line (first lines)]
      (if (blank-line? line)
        (recur (rest lines) acc)
        (recur (rest lines) (conj acc line))))))
(defn- join-fragments [fragments]
  (reduce str "" fragments))
(defn- text-lines [lines]
  (-> (keep-non-blank-lines lines [])
      (str/join "\n")))

(defn wrap-text
  "Plain-text email body with greeting, content lines, and branded closing."
  [customer-name body-lines]
  (let [name (or customer-name "Customer")
        body (if (string? body-lines)
               body-lines
               (text-lines body-lines))]
    (str "Dear " name ",\n\n"
         body
         "\n\n"
         "Service location: " workshop-address "\n\n"
         "Kind regards,\n"
         brand-name)))

(defn- detail-rows-html [pairs]
  (let [visible (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))) pairs)]
    (->> visible
         (map-indexed
          (fn [i [label value]]
            (str "<tr><td class=\"mcp-rule\" style=\"padding:14px 20px;"
                 (when (pos? i) (str "border-top:1px solid " separator ";"))
                 "\">"
                 "<p class=\"mcp-muted\" style=\"margin:0 0 2px;font-family:" font-stack ";"
                 "font-size:14px;line-height:1.43;color:" text-secondary ";\">"
                 (escape-html label)
                 "</p>"
                 "<p class=\"mcp-text\" style=\"margin:0;font-family:" font-stack ";"
                 "font-size:17px;line-height:1.47;color:" text-primary ";\">"
                 (escape-html (str value))
                 "</p>"
                 "</td></tr>")))
         (join-fragments))))

(defn details-table
  "Optional labeled detail block for HTML emails."
  [pairs]
  (let [rows (detail-rows-html pairs)]
    (when-not (str/blank? rows)
      (str "<table role=\"presentation\" class=\"mcp-inset\" width=\"100%\" cellpadding=\"0\" "
           "cellspacing=\"0\" bgcolor=\"" inset-bg "\" "
           "style=\"width:100%;margin:28px 0;border-collapse:separate;"
           "background-color:" inset-bg ";border-radius:12px;\">"
           rows
           "</table>"))))

(defn details-text
  "Labeled detail lines for plain-text emails."
  [pairs]
  (->> pairs
       (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))))
       (map (fn [[label value]] (str label ": " value)))
       (str/join "\n")))

(defn cta-html
  "Pill button + fallback paste link."
  [label url]
  (when (and url (not (str/blank? url)))
    (let [safe-url (escape-html url)
          safe-label (escape-html label)]
      (str "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
           "style=\"margin:28px 0 20px;border-collapse:separate;\"><tr>"
           "<td bgcolor=\"" accent "\" style=\"background-color:" accent ";border-radius:980px;\">"
           "<a href=\"" safe-url "\" "
           "style=\"display:inline-block;padding:12px 24px;font-family:" font-stack ";"
           "font-size:17px;line-height:1.18;font-weight:400;color:#ffffff;"
           "text-decoration:none;border-radius:980px;\">"
           safe-label
           "</a></td></tr></table>"
           "<p class=\"mcp-muted\" style=\"margin:0 0 16px;font-family:" font-stack ";"
           "font-size:14px;line-height:1.43;color:" text-secondary ";\">"
           "If the button doesn’t work, paste this link into your browser:<br/>"
           "<a class=\"mcp-link\" href=\"" safe-url "\" "
           "style=\"color:" link-color ";text-decoration:none;word-break:break-all;\">"
           safe-url "</a>"
           "</p>"))))

(defn- preheader-html [text]
  (when-not (str/blank? text)
    (str "<div style=\"display:none;max-height:0;max-width:0;overflow:hidden;"
         "mso-hide:all;opacity:0;font-size:1px;line-height:1px;color:" page-bg ";\">"
         (escape-html text)
         "</div>")))

(defn- headline-html [eyebrow title]
  (str (when-not (str/blank? eyebrow)
         (str "<p class=\"mcp-muted\" style=\"margin:0 0 8px;font-family:" font-stack ";"
              "font-size:14px;line-height:1.43;font-weight:600;color:" text-secondary ";\">"
              (escape-html eyebrow)
              "</p>"))
       (when-not (str/blank? title)
         (str "<h1 class=\"mcp-text mcp-headline\" style=\"margin:0 0 24px;"
              "font-family:" display-font-stack ";font-size:32px;line-height:1.125;"
              "font-weight:600;letter-spacing:-0.01em;color:" text-primary ";\">"
              (escape-html title)
              "</h1>"))))

(defn- footer-html []
  (let [muted (str "margin:0 0 6px;font-family:" font-stack ";font-size:12px;"
                   "line-height:1.5;color:" text-secondary ";")]
    (str "<tr><td style=\"padding:24px 24px 0;\">"
         "<p class=\"mcp-muted\" style=\"" muted "\">"
         (escape-html workshop-name) " · " (escape-html workshop-address)
         "</p>"
         "<p class=\"mcp-muted\" style=\"" muted "\">"
         "Workshop hours " (escape-html workshop-hours) ", every day."
         "</p>"
         "<p class=\"mcp-muted mcp-rule\" style=\"" muted "margin-top:16px;padding-top:16px;"
         "border-top:1px solid " separator ";\">"
         "You’re receiving this email because you have a " (escape-html brand-name)
         " account or booking."
         "</p>"
         "<p class=\"mcp-muted\" style=\"" muted "\">"
         "Copyright © " (.getYear (LocalDate/now)) " " (escape-html brand-name) "."
         "</p>"
         "</td></tr>")))

(defn wrap-html
  "Full HTML document: white card on a light grey page, mirroring the web app.
  `opts` may contain :title (headline), :eyebrow (small line above it) and
  :preheader (inbox preview text)."
  ([customer-name body-html]
   (wrap-html customer-name body-html {}))
  ([customer-name body-html {:keys [title eyebrow preheader]}]
   (let [safe-name (escape-html (or customer-name "Customer"))
         body-p (str "font-family:" font-stack ";font-size:17px;line-height:1.47;"
                     "color:" text-primary ";")]
     (str "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>"
          "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
          "<meta name=\"color-scheme\" content=\"light dark\"/>"
          "<meta name=\"supported-color-schemes\" content=\"light dark\"/>"
          "<title>" (escape-html (or title brand-name)) "</title>"
          "<style>" head-styles "</style>"
          "</head>"
          "<body class=\"mcp-page\" bgcolor=\"" page-bg "\" "
          "style=\"margin:0;padding:0;background-color:" page-bg ";" body-p "\">"
          (preheader-html preheader)
          "<table role=\"presentation\" class=\"mcp-page\" width=\"100%\" cellpadding=\"0\" "
          "cellspacing=\"0\" bgcolor=\"" page-bg "\" style=\"background-color:" page-bg ";\">"
          "<tr><td align=\"center\" class=\"mcp-shell\" style=\"padding:40px 16px 48px;\">"
          "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
          "style=\"max-width:600px;\">"
          "<tr><td align=\"center\" style=\"padding:0 0 28px;\">"
          "<img src=\"" (logo-data-uri) "\" alt=\"Hussarya\" width=\"120\" height=\"64\" "
          "style=\"display:block;width:120px;height:64px;border:0;\"/>"
          "</td></tr>"
          "<tr><td class=\"mcp-card\" bgcolor=\"" card-bg "\" "
          "style=\"padding:48px 48px 40px;background-color:" card-bg ";border-radius:18px;\">"
          (headline-html eyebrow title)
          "<p class=\"mcp-text\" style=\"margin:0 0 16px;" body-p "\">Dear " safe-name ",</p>"
          body-html
          "<p class=\"mcp-text\" style=\"margin:28px 0 0;" body-p "\">"
          "Kind regards,<br/>"
          (escape-html brand-name)
          "</p>"
          "</td></tr>"
          (footer-html)
          "</table>"
          "</td></tr></table>"
          "</body></html>"))))

(defn p
  "Paragraph fragment for HTML body content."
  [body-text]
  (str "<p class=\"mcp-text\" style=\"margin:0 0 16px;font-family:" font-stack ";"
       "font-size:17px;line-height:1.47;color:" text-primary ";\">"
       (escape-html body-text)
       "</p>"))

(defn p-html
  "Paragraph that already contains trusted escaped HTML (e.g. strong)."
  [html]
  (str "<p class=\"mcp-text\" style=\"margin:0 0 16px;font-family:" font-stack ";"
       "font-size:17px;line-height:1.47;color:" text-primary ";\">"
       html
       "</p>"))

(defn note
  "Smaller secondary paragraph (link expiry, safety notes)."
  [body-text]
  (str "<p class=\"mcp-muted\" style=\"margin:0 0 8px;font-family:" font-stack ";"
       "font-size:14px;line-height:1.43;color:" text-secondary ";\">"
       (escape-html body-text)
       "</p>"))
