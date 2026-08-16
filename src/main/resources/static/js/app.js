/*
 * Progressive enhancement only: every form on this site works with JavaScript
 * disabled. The reveal button is created here rather than in the templates so
 * that a browser without JS never renders a control that would do nothing.
 */
(function () {
  'use strict';

  var EYE_OPEN =
    '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
    'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';

  var EYE_CLOSED =
    '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
    'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>' +
    '<path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>' +
    '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/></svg>';

  function addToggle(input) {
    var wrapper = document.createElement('div');
    wrapper.className = 'password-field';
    input.parentNode.insertBefore(wrapper, input);
    wrapper.appendChild(input);

    var button = document.createElement('button');
    button.type = 'button';           // never submit the form
    button.className = 'password-toggle';
    button.innerHTML = EYE_OPEN;
    button.setAttribute('aria-label', 'Show password');
    button.setAttribute('aria-pressed', 'false');
    wrapper.appendChild(button);

    button.addEventListener('click', function () {
      var revealed = input.type === 'text';
      input.type = revealed ? 'password' : 'text';
      button.innerHTML = revealed ? EYE_OPEN : EYE_CLOSED;
      button.setAttribute('aria-label', revealed ? 'Show password' : 'Hide password');
      button.setAttribute('aria-pressed', revealed ? 'false' : 'true');
      // Keep the caret where the user left it rather than jumping to the end.
      input.focus({ preventScroll: true });
    });
  }

  /*
   * Add to basket without leaving the page.
   *
   * The form is a real form posting to a real endpoint; this only intercepts
   * the submit. Sending its own FormData carries Spring Security's hidden
   * _csrf field with it, and the X-Requested-With header is what selects the
   * JSON handler -- without JavaScript neither happens and the ordinary
   * redirect to the basket runs instead.
   */
  function updateCartCount(count) {
    var badge = document.querySelector('.cart-count');
    if (!badge) {
      return;
    }
    badge.textContent = count;
    badge.classList.toggle('is-empty', count === 0);
    badge.classList.remove('bumped');
    // Force a reflow so the class re-applies even on repeated adds.
    void badge.offsetWidth;
    badge.classList.add('bumped');
    window.setTimeout(function () {
      badge.classList.remove('bumped');
    }, 200);
  }

  function announce(form, text, isError) {
    var status = form.querySelector('.add-status');
    if (!status) {
      status = document.createElement('p');
      status.className = 'add-status';
      // Assertive would interrupt; this is a confirmation, not a warning.
      status.setAttribute('role', 'status');
      form.appendChild(status);
    }
    status.textContent = text;
    status.classList.toggle('is-error', Boolean(isError));
  }

  function enhanceAddToCart(form) {
    var button = form.querySelector('button[type="submit"]');

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      if (button) {
        button.disabled = true;
      }

      fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        headers: { 'X-Requested-With': 'fetch' },
        credentials: 'same-origin'
      })
        .then(function (response) {
          if (!response.ok) {
            throw new Error('Request failed with ' + response.status);
          }
          return response.json();
        })
        .then(function (summary) {
          updateCartCount(summary.itemCount);
          announce(form, summary.message, false);
        })
        .catch(function () {
          // Anything unexpected -- an expired session, a network drop -- falls
          // back to the plain form post, which lands somewhere that explains
          // itself rather than failing silently here.
          form.submit();
        })
        .finally(function () {
          if (button) {
            button.disabled = false;
          }
        });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('input[type="password"]').forEach(addToggle);
    document.querySelectorAll('form.add-to-cart').forEach(enhanceAddToCart);
  });
})();
