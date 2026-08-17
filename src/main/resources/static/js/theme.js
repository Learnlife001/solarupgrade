/*
 * Theme selection.
 *
 * This file is loaded *without* defer, in the head, on purpose. It has to run
 * before the first paint: if it ran after, a reader who chose dark would see a
 * white flash on every page load. It is deliberately tiny for the same reason —
 * it blocks rendering, so it must not do anything slow.
 *
 * Light is the default. Only an explicit choice, stored in localStorage,
 * switches the page to dark; the operating system's setting is not consulted,
 * because a dark laptop should not hide the light design from someone who
 * never asked for dark on this site.
 */
(function () {
  'use strict';

  var KEY = 'solarupgrade-theme';

  function stored() {
    try {
      return window.localStorage.getItem(KEY);
    } catch (e) {
      // Private browsing and blocked storage both throw here. Falling back to
      // the default theme is better than failing to render the page.
      return null;
    }
  }

  function apply(theme) {
    if (theme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }

  apply(stored());

  // The button itself is added once the header exists. It is created here
  // rather than in the template so that a browser with JavaScript disabled
  // never shows a switch that could not do anything.
  document.addEventListener('DOMContentLoaded', function () {
    var host = document.querySelector('.header-actions');
    if (!host) {
      return;
    }

    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'theme-toggle';

    function label() {
      var dark = document.documentElement.getAttribute('data-theme') === 'dark';
      // The label names the destination, not the current state: pressing a
      // button that says "Dark" should give you dark.
      button.textContent = dark ? '☀ Light' : '☽ Dark';
      button.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    }

    button.addEventListener('click', function () {
      var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      apply(next);
      try {
        window.localStorage.setItem(KEY, next);
      } catch (e) {
        // Choice will not survive the next page load; the current page is
        // still switched, which is the part the reader asked for.
      }
      label();
    });

    label();
    host.insertBefore(button, host.firstChild);
  });
})();
