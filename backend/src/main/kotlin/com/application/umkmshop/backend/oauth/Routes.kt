package com.application.umkmshop.backend.oauth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URLEncoder
import kotlinx.serialization.json.*
import com.application.umkmshop.backend.oauth.templates.*

fun Application.oauthRoutes(service: OAuthService, supabaseAuth: SupabaseAuthService? = null) {
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
                // Log mismatch detail to terminal
                println("OAuth Mismatch: ${error.message}")
                println("  Requested Client: ${request.clientId}")
                println("  Requested URI: '${request.redirectUri}'")
                
                call.respondOAuthError(error, HttpStatusCode.BadRequest)
                return@get
            }
            
            val supabaseUser = if (supabaseAuth != null) {
                val token = call.request.queryParameters["access_token"]
                    ?: call.request.cookies["sb-access-token"]
                    ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")
                
                token?.let { supabaseAuth.verify(it) }
            } else null

            if (supabaseUser != null) {
                val token = call.request.queryParameters["access_token"]
                    ?: call.request.cookies["sb-access-token"]
                    ?: ""
                call.respondText(consentPage(client, request, supabaseUser, token), ContentType.Text.Html)
            } else {
                call.respondText(loginPage(client, request), ContentType.Text.Html)
            }
        }

        post("/oauth/authorize") {
            val params = call.receiveParameters()
            val request = AuthorizationRequest(
                responseType = params["oauth_response_type"] ?: params["response_type"].orEmpty(),
                clientId = params["oauth_client_id"] ?: params["client_id"].orEmpty(),
                redirectUri = params["oauth_redirect_uri"] ?: params["redirect_uri"].orEmpty(),
                scope = params["oauth_scope"] ?: params["scope"].orEmpty(),
                state = params["oauth_state"] ?: params["state"],
                codeChallenge = params["oauth_code_challenge"] ?: params["code_challenge"],
                codeChallengeMethod = params["oauth_code_challenge_method"] ?: params["code_challenge_method"],
                nonce = params["oauth_nonce"] ?: params["nonce"],
            )
            
            val token = params["access_token"] 
                ?: call.request.cookies["sb-access-token"]
            
            val supabaseUser = if (supabaseAuth != null && token != null) {
                supabaseAuth.verify(token)
            } else null
            
            if (supabaseUser == null) {
                call.respondRedirect("/oauth/authorize?${call.request.queryString()}")
                return@post
            }
            
            val approved = params["approve"] == "true"
            val redirect = if (approved) {
                runCatching {
                    service.redirectWithCode(
                        redirectUri = request.redirectUri,
                        code = service.approveAuthorization(request, supabaseUser),
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

        post("/oauth/login") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""
            val password = params["password"] ?: ""
            
            if (supabaseAuth == null) {
                call.respondText("Auth not configured", status = HttpStatusCode.InternalServerError)
                return@post
            }

            val token = supabaseAuth.signIn(email, password)
            if (token != null) {
                call.response.cookies.append(
                    name = "sb-access-token",
                    value = token,
                    httpOnly = true,
                    path = "/",
                    maxAge = 3600L
                )
                
                val oauthParams = mutableMapOf(
                    "response_type" to params["oauth_response_type"],
                    "client_id" to params["oauth_client_id"],
                    "redirect_uri" to params["oauth_redirect_uri"],
                    "scope" to params["oauth_scope"],
                    "state" to params["oauth_state"],
                    "code_challenge" to params["oauth_code_challenge"],
                    "code_challenge_method" to params["oauth_code_challenge_method"],
                    "nonce" to params["oauth_nonce"],
                    "access_token" to token
                ).filter { it.value != null }
                
                val queryString = oauthParams.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value!!, "UTF-8")}" }
                call.respondRedirect("/oauth/authorize?$queryString")
            } else {
                val request = AuthorizationRequest(
                    responseType = params["oauth_response_type"].orEmpty(),
                    clientId = params["oauth_client_id"].orEmpty(),
                    redirectUri = params["oauth_redirect_uri"].orEmpty(),
                    scope = params["oauth_scope"].orEmpty(),
                    state = params["oauth_state"],
                    codeChallenge = params["oauth_code_challenge"],
                    codeChallengeMethod = params["oauth_code_challenge_method"],
                    nonce = params["oauth_nonce"]
                )
                val client = service.validateAuthorizationRequest(request)
                call.respondText(loginPage(client, request, "Email atau password salah."), ContentType.Text.Html)
            }
        }

        get("/oauth/signup") {
            val request = call.authorizationRequestFromQuery()
            val client = service.validateAuthorizationRequest(request)
            call.respondText(signupPage(client, request), ContentType.Text.Html)
        }

        post("/oauth/signup") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""
            val password = params["password"] ?: ""
            val name = params["name"] ?: ""

            if (supabaseAuth != null && supabaseAuth.signUp(email, password, name)) {
                val oauthParams = mutableMapOf(
                    "response_type" to params["oauth_response_type"],
                    "client_id" to params["oauth_client_id"],
                    "redirect_uri" to params["oauth_redirect_uri"],
                    "scope" to params["oauth_scope"],
                    "state" to params["oauth_state"],
                    "code_challenge" to params["oauth_code_challenge"],
                    "code_challenge_method" to params["oauth_code_challenge_method"],
                    "nonce" to params["oauth_nonce"]
                ).filter { it.value != null }
                
                val queryString = oauthParams.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value!!, "UTF-8")}" }
                call.respondRedirect("/oauth/authorize?$queryString")
            } else {
                call.respondText("Pendaftaran gagal.", status = HttpStatusCode.BadRequest)
            }
        }

        get("/oauth/userinfo") {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val token = authHeader.removePrefix("Bearer ")
            try {
                val claims = service.verifyAccessToken(token)
                val userId = claims["sub"]?.jsonPrimitive?.contentOrNull ?: ""
                val user = service.findUserById(userId) ?: throw Exception("Not found")
                call.respondText("""{"sub":"${user.id}","email":"${user.email}","name":"${user.name}"}""", ContentType.Application.Json)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }

        post("/api/test-push") {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val token = authHeader.removePrefix("Bearer ")
            try {
                val claims = service.verifyAccessToken(token)
                val userId = claims["sub"]?.jsonPrimitive?.contentOrNull ?: throw Exception("No sub")
                service.enqueueTestNotification(userId)
                call.respondText("""{"status":"success"}""", ContentType.Application.Json)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

private fun ApplicationCall.authorizationRequestFromQuery() = AuthorizationRequest(
    responseType = request.queryParameters["response_type"].orEmpty(),
    clientId = request.queryParameters["client_id"].orEmpty(),
    redirectUri = request.queryParameters["redirect_uri"].orEmpty(),
    scope = request.queryParameters["scope"].orEmpty(),
    state = request.queryParameters["state"],
    codeChallenge = request.queryParameters["code_challenge"],
    codeChallengeMethod = request.queryParameters["code_challenge_method"],
    nonce = request.queryParameters["nonce"],
)

private suspend fun ApplicationCall.respondOAuthError(error: OAuthError, status: HttpStatusCode) {
    respondText(
        """{"error":"${error.code}","error_description":"${error.message}"}""",
        ContentType.Application.Json,
        status,
    )
}
