# Long-session loading investigation

Base: `aimdi140`, `2ce40d7d6060f575596d9d2cdf27f0c96e9f21be`.

Report: after using XTA for a while, loading stops across the app. No device
trace or reliable sequence was supplied, so the complete phone failure is not
yet reproduced. The following concrete failure paths were identified in the
released code and are covered by this patch.

## Findings and changes

- Group feed rebuilds unconditionally restarted every plugin load, even when
  membership had not changed. Repeated rebuilds could cancel useful work and
  restart its deadline. Unchanged source keys now preserve in-flight work and
  completed results; explicit refresh still starts a new load.
- Group shells retained listeners on the app-scoped combined-groups store and
  did not destroy replaced/disposed group models. Home also retained several
  store listeners. The owners now remove their observers and release models.
  Group reads have a deadline and reject late results after replacement or
  disposal.
- Reddit token refresh had no deadline. After the cached access token expired,
  one stalled refresh could permanently occupy the shared token future, so
  later Reddit reads and retries waited on it again. The token request now
  aborts after 15 seconds, including a stalled response body. Transient failures
  preserve sign-in; a subsequent attempt can renew the token.
- Automatic reader recovery exhausted its budget without renewing it on app
  resume or an online-to-online network handover. Recovery now recognizes
  those events, coalesces nearby signals, and keeps retries bounded and limited
  to visible reading surfaces. Android sends network identity alongside its
  existing validation state.

The Reddit bug alone cannot explain independent X and Bluesky failures.
These changes fix demonstrated failure paths; they are not evidence that every
possible cause of the reported full-app freeze has been eliminated.

## Validation

Regression coverage includes stalled request headers and bodies, expiry with
multiple token waiters, successful retry without losing sign-in, unchanged-feed
rebuilds, group read cancellation and late results, observer disposal, exhausted
retry budgets, duplicate network signals, handover, and background/foreground.

Local Flutter dependency setup was blocked by automatic approval review after
an unexpected cloud-metadata request. No local Flutter pass is claimed. Use the
pull request's existing verification workflow for analysis and test results.
The pinned Flutter/dependency versions, `lib/client/`, and `lib/database/` are
unchanged.

## Phone verification

1. Open and close several group feeds repeatedly, then change group membership
   and switch between Following, X, Bluesky, Substack, and Reddit.
2. Interrupt a load, switch between working Wi-Fi and mobile data, and check
   that the visible feed retries while hidden feeds remain quiet.
3. Leave a failed feed until its retries are exhausted, background the app,
   then resume it with a working connection. Check that loading can recover.
4. Keep a Reddit session open beyond access-token expiry and repeat loading
   after a temporary connection failure; sign-in should remain available.
5. If the full freeze recurs, capture the installed Aimdi release, last action,
   whether navigation still responds, and XTA's reader diagnostics before
   force-stopping it. Compare memory after repeated group navigation.

Repository verification does not substitute for this device session. This
patch does not itself bump a release tag or publish an APK.
