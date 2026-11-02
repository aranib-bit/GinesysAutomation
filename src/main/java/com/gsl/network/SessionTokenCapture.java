package com.gsl.network;

import com.gsl.api.core.SessionTokens;
import com.gsl.api.core.WebApiSessionClient;
import com.gsl.api.webapi.model.response.auth.TokenResponse;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.network.Network;
import org.openqa.selenium.devtools.v153.network.model.RequestId;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the WEBAPI tokens the application's own login receives, so a backfill can reuse the browser user's session
 * instead of logging a second user in ({@link SessionTokens}). Chrome/Edge only (CDP): it watches the POST to the
 * login path ({@code webapi.loginPath}), and once that response has fully loaded reads its body - the body is not
 * available any earlier - and keeps the {@code access_token}/{@code access_token2} pair. Later token responses
 * (re-login, refresh) overwrite the pair. Independent of the network-capture settings, so it works with capture off.
 * Tokens are never logged.
 */
public final class SessionTokenCapture {

    private SessionTokenCapture() {}

    /** Starts listening on {@code driver}; a driver without CDP (e.g. Firefox) is left alone, and its backfills use a second user as before. */
    public static void attach(WebDriver driver) {
        if (!(driver instanceof HasDevTools hasDevTools)) {
            ReportLogger.network("[SessionTokenCapture] Driver has no CDP - session tokens are not captured; backfills use a second WEBAPI user.");
            return;
        }
        try {
            String loginPath = ConfigFactory.getApiConfig().webApiLoginPath();
            DevTools devTools = hasDevTools.getDevTools();
            devTools.createSessionIfThereIsNotOne();
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

            Set<String> tokenRequests = ConcurrentHashMap.newKeySet();
            devTools.addListener(Network.requestWillBeSent(), req -> {
                if ("POST".equalsIgnoreCase(req.getRequest().getMethod()) && req.getRequest().getUrl().contains(loginPath)) {
                    tokenRequests.add(req.getRequestId().toString());
                }
            });
            devTools.addListener(Network.loadingFinished(), done -> {
                String id = done.getRequestId().toString();
                if (!tokenRequests.remove(id)) return;
                try {
                    Network.GetResponseBodyResponse body = devTools.send(Network.getResponseBody(new RequestId(id)));
                    TokenResponse token = TokenResponse.parse(body.getBody());
                    if (token.getAccessToken() == null) return; // an error body (e.g. code 012) carries no token
                    String userId = WebApiSessionClient.userIdFromToken(token.getAccessToken2());
                    SessionTokens.put(driver, new SessionTokens.Captured(token.getAccessToken(), token.getAccessToken2(), userId));
                    ReportLogger.network("[SessionTokenCapture] Captured the browser session's WEBAPI tokens (userId={}).", userId);
                } catch (Exception e) {
                    ReportLogger.networkWarn("[SessionTokenCapture] Could not read the token response: {}", e.getMessage());
                }
            });
            ReportLogger.network("[SessionTokenCapture] Listening for the login response on '{}'.", loginPath);
        } catch (Exception e) {
            ReportLogger.networkWarn("[SessionTokenCapture] Not started: {}", e.getMessage());
        }
    }
}
