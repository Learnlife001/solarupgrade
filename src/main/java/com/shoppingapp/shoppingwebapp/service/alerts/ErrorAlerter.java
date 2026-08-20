package com.shoppingapp.shoppingwebapp.service.alerts;

import com.shoppingapp.shoppingwebapp.config.Brand;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import com.shoppingapp.shoppingwebapp.service.email.EmailHtml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.shoppingapp.shoppingwebapp.service.email.EmailHtml.escape;

/**
 * Emails whoever runs the shop when a page fails.
 *
 * <p>Written because of how the last outage was found: {@code /suppliers}
 * answered every request with a 500 for twenty-five minutes and the way anybody
 * learned of it was a screenshot. Nothing in the system had an opinion about
 * it. The logs held the stack trace the whole time, on a dashboard nobody was
 * looking at, which is the same as not having it.
 *
 * <p>This is deliberately not a monitoring product. It has no dashboard, no
 * grouping across deploys and no trend line -- it sends an email the first time
 * something breaks. That is the difference between finding out in a minute and
 * finding out when a customer bothers to tell you, and it needs no account
 * anywhere. Sentry or its like is the upgrade when the shop is busy enough to
 * want the trends.
 *
 * <p>The address list is {@code app.alerts.recipients}, defaulting to the
 * administrators. With none configured this quietly does nothing, so a fresh
 * deployment is not held up by it.
 */
@Component
public class ErrorAlerter {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlerter.class);

    /** Frames from our own code, which are the ones that say where to look. */
    private static final String OWN_PACKAGE = "com.shoppingapp.shoppingwebapp";

    private static final int FRAMES_IN_EMAIL = 12;

    private final EmailService email;
    private final Brand brand;
    private final List<String> recipients;
    private final boolean enabled;
    private final AlertBudget budget;

    public ErrorAlerter(EmailService email,
                        Brand brand,
                        @Value("${app.alerts.enabled:true}") boolean enabled,
                        @Value("${app.alerts.recipients:}") String recipients,
                        @Value("${app.alerts.cooldown-minutes:30}") int cooldownMinutes,
                        @Value("${app.alerts.max-per-hour:10}") int maxPerHour) {
        this.email = email;
        this.brand = brand;
        this.enabled = enabled;
        this.recipients = Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .toList();
        this.budget = new AlertBudget(Duration.ofMinutes(cooldownMinutes), maxPerHour);

        if (enabled && this.recipients.isEmpty()) {
            log.info("Error alerting is on but no recipients are configured; "
                    + "set app.alerts.recipients or app.admin.emails to receive them");
        }
    }

    /**
     * Reports one failed request.
     *
     * <p>Never throws. An alerter that can fail a request it was only meant to
     * observe would be a worse bug than the one it is reporting.
     */
    public void requestFailed(String method, String path, Throwable error) {
        try {
            if (!enabled || recipients.isEmpty() || error == null) {
                return;
            }
            AlertBudget.Decision decision = budget.record(signatureOf(path, error), Instant.now());
            if (!decision.send()) {
                return;
            }
            send(method, path, error, decision.alsoSuppressed());
        } catch (Exception ex) {
            log.warn("Could not raise an error alert", ex);
        }
    }

    /**
     * Reports a scheduled job that threw.
     *
     * <p>Nothing else does. {@code ErrorAlertResolver} watches requests through
     * the dispatcher, and a job has none -- so the reminder job could fail
     * every hour for a week with the only trace a log line nobody reads, while
     * unpaid orders held stock that was never released. A job failing is
     * quieter than a page failing and matters for longer, because no customer
     * is there to notice.
     *
     * @param what the piece of work that failed, which is also what groups it:
     *             one broken order must not silence a report of the whole job
     *             falling over
     */
    public void jobFailed(String jobName, String what, Throwable error) {
        try {
            if (!enabled || recipients.isEmpty() || error == null) {
                return;
            }
            String signature = "job:" + jobName + ":" + what + ":" + rootCause(error).getClass().getName();
            AlertBudget.Decision decision = budget.record(signature, Instant.now());
            if (!decision.send()) {
                return;
            }
            sendJob(jobName, what, rootCause(error), decision.alsoSuppressed());
        } catch (Exception ex) {
            log.warn("Could not raise a job alert", ex);
        }
    }

    private void sendJob(String jobName, String what, Throwable root, int alsoSuppressed) {
        String subject = "[" + brand.getName() + "] " + jobName + " failed: "
                + root.getClass().getSimpleName();

        StringBuilder detail = new StringBuilder();
        detail.append(EmailHtml.heading("A scheduled job failed"))
                .append(EmailHtml.lead("<strong>" + escape(jobName) + "</strong> failed on "
                        + escape(what) + ". Nobody is watching a job the way somebody watches "
                        + "a page, so it will keep failing until it is looked at."));
        if (alsoSuppressed > 0) {
            detail.append(EmailHtml.paragraph("<strong>" + alsoSuppressed
                    + " more</strong> of these were not reported while it was quiet."));
        }
        detail.append(EmailHtml.divider())
                .append(EmailHtml.sectionTitle("What broke"))
                .append(EmailHtml.paragraph("<strong>" + escape(root.getClass().getName()) + "</strong><br>"
                        + escape(root.getMessage() == null ? "(no message)" : root.getMessage())))
                .append(EmailHtml.sectionTitle("Where"))
                .append("<pre style=\"margin:0;padding:14px;background:" + EmailHtml.SURFACE_2
                        + ";border-radius:10px;overflow-x:auto;font:400 12px/1.6 ui-monospace,"
                        + "SFMono-Regular,Menlo,Consolas,monospace;color:" + EmailHtml.INK + ";\">"
                        + escape(frames(root)) + "</pre>");

        String html = EmailHtml.document(brand.getName(), brand.getMark(), brand.getTagline(),
                "A scheduled job failed", jobName + " failed", null, detail.toString(),
                "You receive this because you are listed in app.alerts.recipients.");

        String text = jobName + " failed on " + what + ".\n\n"
                + root.getClass().getName() + "\n"
                + (root.getMessage() == null ? "(no message)" : root.getMessage()) + "\n\n"
                + frames(root) + "\n";

        for (String recipient : recipients) {
            email.sendOperationalAlert(recipient, subject, text, html);
        }
    }

    /**
     * What counts as "the same error".
     *
     * <p>The path, the exception type and the first frame of our own code. Not
     * the message: a message usually carries the id that varies between
     * occurrences ("No supplier with id 7"), and grouping on it would make
     * every occurrence unique and defeat the cooldown entirely.
     */
    private String signatureOf(String path, Throwable error) {
        Throwable root = rootCause(error);
        String frame = Arrays.stream(root.getStackTrace())
                .filter(element -> element.getClassName().startsWith(OWN_PACKAGE))
                .findFirst()
                .map(element -> element.getClassName() + "." + element.getMethodName())
                .orElse("(no frame in our code)");
        return path + " " + root.getClass().getName() + " " + frame;
    }

    private void send(String method, String path, Throwable error, int alsoSuppressed) {
        Throwable root = rootCause(error);
        String subject = "[" + brand.getName() + "] " + method + " " + path
                + " failed: " + root.getClass().getSimpleName();

        String body = buildHtml(method, path, root, alsoSuppressed);
        String text = buildText(method, path, root, alsoSuppressed);

        for (String recipient : recipients) {
            email.sendOperationalAlert(recipient, subject, text, body);
        }
    }

    private String buildHtml(String method, String path, Throwable root, int alsoSuppressed) {
        StringBuilder detail = new StringBuilder();
        detail.append(EmailHtml.heading("A request failed"))
                .append(EmailHtml.lead(escape(method) + " <strong>" + escape(path)
                        + "</strong> returned an error to whoever asked for it."));

        if (alsoSuppressed > 0) {
            detail.append(EmailHtml.paragraph("<strong>" + alsoSuppressed
                    + " more occurrence" + (alsoSuppressed == 1 ? "" : "s")
                    + "</strong> of this were not reported while it was quiet."));
        }

        detail.append(EmailHtml.divider())
                .append(EmailHtml.sectionTitle("What broke"))
                .append(EmailHtml.paragraph("<strong>" + escape(root.getClass().getName()) + "</strong><br>"
                        + escape(root.getMessage() == null ? "(no message)" : root.getMessage())))
                .append(EmailHtml.sectionTitle("Where"))
                .append("<pre style=\"margin:0;padding:14px;background:" + EmailHtml.SURFACE_2
                        + ";border-radius:10px;overflow-x:auto;font:400 12px/1.6 ui-monospace,"
                        + "SFMono-Regular,Menlo,Consolas,monospace;color:" + EmailHtml.INK + ";\">"
                        + escape(frames(root)) + "</pre>")
                .append(EmailHtml.small("Sent at " + Instant.now()
                        + ". Alerts for this fault are quiet for a while now, so this inbox "
                        + "stays readable."));

        return EmailHtml.document(brand.getName(), brand.getMark(), brand.getTagline(),
                "A request failed", method + " " + path + " failed",
                null, detail.toString(),
                "You receive this because you are listed in app.alerts.recipients.");
    }

    private String buildText(String method, String path, Throwable root, int alsoSuppressed) {
        StringBuilder text = new StringBuilder();
        text.append(method).append(' ').append(path).append(" failed.\n\n");
        if (alsoSuppressed > 0) {
            text.append(alsoSuppressed).append(" more occurrences were not reported while quiet.\n\n");
        }
        text.append(root.getClass().getName()).append('\n')
                .append(root.getMessage() == null ? "(no message)" : root.getMessage())
                .append("\n\n")
                .append(frames(root))
                .append("\n\nYou receive this because you are listed in app.alerts.recipients.\n");
        return text.toString();
    }

    /**
     * The top of the stack, our own frames first.
     *
     * <p>A full trace in an email is forty lines of framework internals around
     * the two that matter, and on a phone that means scrolling past all of it.
     */
    private String frames(Throwable root) {
        List<String> lines = new ArrayList<>();
        for (StackTraceElement element : root.getStackTrace()) {
            if (lines.size() >= FRAMES_IN_EMAIL) {
                break;
            }
            boolean ours = element.getClassName().startsWith(OWN_PACKAGE);
            if (ours || lines.size() < 4) {
                lines.add((ours ? "> " : "  ") + element);
            }
        }
        return String.join("\n", lines);
    }

    /** The cause worth naming, not the wrapper Spring put around it. */
    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Exposed for the tests, which assert on what a fresh alerter would do. */
    boolean isEnabled() {
        return enabled && !recipients.isEmpty();
    }

    static String describe(String method, String path) {
        return (method == null ? "GET" : method.toUpperCase(Locale.ROOT)) + " " + path;
    }
}
