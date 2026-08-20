package com.shoppingapp.shoppingwebapp.service.alerts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Watches requests fail and tells {@link ErrorAlerter}, without changing what
 * the customer sees.
 *
 * <p>It is a {@link HandlerExceptionResolver} that always returns null. Spring
 * asks each resolver in turn until one handles the exception, so returning null
 * puts this first in the queue purely to look: the existing error page, status
 * code and logging all happen exactly as before. Handling the exception here
 * instead would mean this class owning the error page, which is a much larger
 * thing to get wrong.
 *
 * <p><b>What it does not see:</b> failures thrown by servlet filters, which run
 * before the dispatcher — Spring Security's chain among them. Those still reach
 * the logs. Covering them as well would mean a filter of our own wrapped around
 * everything, and a bug in that would take the whole site down rather than one
 * page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorAlertResolver implements HandlerExceptionResolver {

    private final ErrorAlerter alerter;

    public ErrorAlertResolver(ErrorAlerter alerter) {
        this.alerter = alerter;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception exception) {
        if (worthReporting(exception)) {
            alerter.requestFailed(request.getMethod(), request.getRequestURI(), exception);
        }
        // Always null: this resolver observes, it does not resolve.
        return null;
    }

    /**
     * Only faults that are ours.
     *
     * <p>A missing page, a bad parameter or a rejected upload is the request
     * being wrong, not the shop being broken, and Spring models all of them as
     * exceptions. Alerting on them would fill the inbox with other people's
     * typos and crawler traffic -- and an inbox full of noise is one nobody
     * reads when the real thing arrives.
     */
    private boolean worthReporting(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().is5xxServerError();
        }
        // A client that hung up mid-response is not a fault worth waking to.
        String name = exception.getClass().getName();
        return !name.contains("ClientAbortException") && !name.contains("AsyncRequestNotUsableException");
    }
}
