package com.application.umkmshop.backend.oauth

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.oauthRoutes(service: OAuthService, demoUser: OAuthUser) {
    routing {
        get("/.well-known/openid-configuration") {
            call.respondText(service.discoveryJson(), ContentType.Application.Json)
        }

        get("/oauth/jwks.json") {
            call.respondText(service.jwksJson(), ContentType.Application.Json)
        }

        get("/oauth/authorize") {
            val request = call.authorizationRequestFromQuery()
            val client = try {
                service.validateAuthorizationRequest(request)
            } catch (error: OAuthError) {
                call.respondOAuthError(error, HttpStatusCode.BadRequest)
                return@get
            }
            call.respondText(consentPage(client, request, demoUser), ContentType.Text.Html)
        }

        post("/oauth/authorize") {
            val params = call.receiveParameters()
            val request = AuthorizationRequest(
                responseType = params["response_type"].orEmpty(),
                clientId = params["client_id"].orEmpty(),
                redirectUri = params["redirect_uri"].orEmpty(),
                scope = params["scope"].orEmpty(),
                state = params["state"],
                codeChallenge = params["code_challenge"],
                codeChallengeMethod = params["code_challenge_method"],
                nonce = params["nonce"],
            )
            val approved = params["approve"] == "true"
            val redirect = if (approved) {
                runCatching {
                    service.redirectWithCode(
                        redirectUri = request.redirectUri,
                        code = service.approveAuthorization(request, demoUser),
                        state = request.state,
                    )
                }.getOrElse { error ->
                    val oauthError = error as? OAuthError ?: OAuthError("server_error", "Authorization failed.")
                    service.redirectWithError(request.redirectUri, oauthError.code, request.state)
                }
            } else {
                service.redirectWithError(request.redirectUri, "access_denied", request.state)
            }
            call.respondRedirect(redirect)
        }

        post("/oauth/token") {
            val params = call.receiveParameters()
            val response = try {
                when (params["grant_type"]) {
                    "authorization_code" -> service.exchangeAuthorizationCode(
                        clientId = params["client_id"].orEmpty(),
                        clientSecret = params["client_secret"],
                        code = params["code"].orEmpty(),
                        redirectUri = params["redirect_uri"].orEmpty(),
                        codeVerifier = params["code_verifier"],
                    )
                    "refresh_token" -> service.refresh(
                        clientId = params["client_id"].orEmpty(),
                        clientSecret = params["client_secret"],
                        refreshToken = params["refresh_token"].orEmpty(),
                    )
                    else -> throw OAuthError("unsupported_grant_type", "Only authorization_code and refresh_token are supported.")
                }
            } catch (error: OAuthError) {
                call.respondOAuthError(error, HttpStatusCode.BadRequest)
                return@post
            }
            call.respondText(response.toJson(), ContentType.Application.Json)
        }

        get("/oauth/userinfo") {
            val bearer = call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
            if (bearer == null) {
                call.respondOAuthError(OAuthError("invalid_token", "Bearer access token is required."), HttpStatusCode.Unauthorized)
                return@get
            }
            val response = try {
                service.userInfo(bearer)
            } catch (error: OAuthError) {
                call.respondOAuthError(error, HttpStatusCode.Unauthorized)
                return@get
            }
            call.respondText(response, ContentType.Application.Json)
        }

        post("/oauth/revoke") {
            val params = call.receiveParameters()
            service.revoke(params["token"].orEmpty())
            call.respondText("", status = HttpStatusCode.OK)
        }

        get("/demo") {
            call.respondText(demoClientPage(), ContentType.Text.Html)
        }

        get("/demo/callback") {
            call.respondText(demoCallbackPage(), ContentType.Text.Html)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.authorizationRequestFromQuery(): AuthorizationRequest =
    AuthorizationRequest(
        responseType = request.queryParameters["response_type"].orEmpty(),
        clientId = request.queryParameters["client_id"].orEmpty(),
        redirectUri = request.queryParameters["redirect_uri"].orEmpty(),
        scope = request.queryParameters["scope"].orEmpty(),
        state = request.queryParameters["state"],
        codeChallenge = request.queryParameters["code_challenge"],
        codeChallengeMethod = request.queryParameters["code_challenge_method"],
        nonce = request.queryParameters["nonce"],
    )

private suspend fun io.ktor.server.application.ApplicationCall.respondOAuthError(error: OAuthError, status: HttpStatusCode) {
    respondText("""{"error":"${escapeJson(error.code)}","error_description":"${escapeJson(error.message)}"}""", ContentType.Application.Json, status)
}

private fun consentPage(client: OAuthClient, request: AuthorizationRequest, user: OAuthUser): String =
    """
    <!doctype html>
    <html lang="id">
      <head><meta charset="utf-8"><title>UMKMShop OAuth Consent</title></head>
      <body>
        <main style="font-family: system-ui; max-width: 640px; margin: 40px auto;">
          <h1>Izinkan ${escapeHtml(client.clientName)}?</h1>
          <p>Login sebagai ${escapeHtml(user.email)}.</p>
          <dl>
            <dt>Redirect URI</dt><dd>${escapeHtml(request.redirectUri)}</dd>
            <dt>Scope</dt><dd>${escapeHtml(request.scope)}</dd>
          </dl>
          <form method="post" action="/oauth/authorize">
            ${hidden("response_type", request.responseType)}
            ${hidden("client_id", request.clientId)}
            ${hidden("redirect_uri", request.redirectUri)}
            ${hidden("scope", request.scope)}
            ${hidden("state", request.state.orEmpty())}
            ${hidden("code_challenge", request.codeChallenge.orEmpty())}
            ${hidden("code_challenge_method", request.codeChallengeMethod.orEmpty())}
            ${hidden("nonce", request.nonce.orEmpty())}
            <button name="approve" value="true">Approve</button>
            <button name="approve" value="false">Deny</button>
          </form>
        </main>
      </body>
    </html>
    """.trimIndent()

private fun demoClientPage(): String =
    """
    <!doctype html>
    <html lang="id">
      <head><meta charset="utf-8"><title>UMKMShop OAuth Demo Client</title></head>
      <body>
        <main style="font-family: system-ui; max-width: 760px; margin: 40px auto;">
          <h1>UMKMShop OAuth Demo Client</h1>
          <button id="start">Start Authorization Code + PKCE</button>
          <pre id="output"></pre>
        </main>
        <script>
          const issuer = window.location.origin;
          const clientId = "umkmshop-demo-public";
          const redirectUri = issuer + "/demo/callback";
          const output = document.getElementById("output");
          function base64Url(buffer) {
            return btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
          }
          async function sha256(value) {
            return crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
          }
          function randomString() {
            return base64Url(crypto.getRandomValues(new Uint8Array(32)));
          }
          document.getElementById("start").onclick = async () => {
            const verifier = randomString();
            const challenge = base64Url(await sha256(verifier));
            const state = randomString();
            sessionStorage.setItem("pkce_verifier", verifier);
            sessionStorage.setItem("oauth_state", state);
            const params = new URLSearchParams({
              response_type: "code",
              client_id: clientId,
              redirect_uri: redirectUri,
              scope: "openid email profile",
              state,
              nonce: randomString(),
              code_challenge: challenge,
              code_challenge_method: "S256"
            });
            window.location = issuer + "/oauth/authorize?" + params.toString();
          };
        </script>
      </body>
    </html>
    """.trimIndent()

private fun demoCallbackPage(): String =
    """
    <!doctype html>
    <html lang="id">
      <head><meta charset="utf-8"><title>UMKMShop OAuth Callback</title></head>
      <body>
        <main style="font-family: system-ui; max-width: 760px; margin: 40px auto;">
          <h1>OAuth Callback</h1>
          <pre id="output"></pre>
        </main>
        <script>
          const output = document.getElementById("output");
          const issuer = window.location.origin;
          const params = new URLSearchParams(window.location.search);
          async function run() {
            if (params.get("error")) {
              output.textContent = "Authorization error: " + params.get("error");
              return;
            }
            if (params.get("state") !== sessionStorage.getItem("oauth_state")) {
              output.textContent = "State mismatch";
              return;
            }
            const body = new URLSearchParams({
              grant_type: "authorization_code",
              client_id: "umkmshop-demo-public",
              code: params.get("code"),
              redirect_uri: issuer + "/demo/callback",
              code_verifier: sessionStorage.getItem("pkce_verifier")
            });
            const tokenResponse = await fetch("/oauth/token", { method: "POST", body });
            const tokens = await tokenResponse.json();
            const userInfoResponse = await fetch("/oauth/userinfo", {
              headers: { Authorization: "Bearer " + tokens.access_token }
            });
            const userInfo = await userInfoResponse.json();
            output.textContent = JSON.stringify({ tokens, userInfo }, null, 2);
          }
          run();
        </script>
      </body>
    </html>
    """.trimIndent()

private fun hidden(name: String, value: String): String =
    """<input type="hidden" name="$name" value="${escapeHtml(value)}">"""

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
