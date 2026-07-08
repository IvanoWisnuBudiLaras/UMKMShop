package com.application.umkmshop.backend.oauth.templates

import com.application.umkmshop.backend.oauth.AuthorizationRequest
import com.application.umkmshop.backend.oauth.OAuthClient
import com.application.umkmshop.backend.oauth.OAuthUser
import java.net.URLEncoder

fun escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun hidden(name: String, value: String) =
    """<input type="hidden" name="$name" value="${escapeHtml(value)}">"""

private fun head(title: String) = """
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <link href="https://fonts.googleapis.com/css2?family=Google+Sans:wght@400;500&display=swap" rel="stylesheet">
        <style>
          body { font-family: 'Google Sans', sans-serif; background: #fff; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; color: #3c4043; }
          .card { border: 1px solid #dadce0; border-radius: 8px; padding: 48px 40px 36px; width: 450px; max-width: 90%; box-sizing: border-box; text-align: center; }
          .logo { color: #1a73e8; font-size: 24px; font-weight: 500; margin-bottom: 10px; display: flex; align-items: center; justify-content: center; gap: 8px; }
          .logo-dot { width: 8px; height: 8px; background: #1a73e8; border-radius: 50%; }
          h1 { font-size: 24px; font-weight: 400; margin: 0 0 8px; color: #202124; }
          .subtitle { font-size: 16px; margin-bottom: 24px; }
          .user-box { border: 1px solid #dadce0; border-radius: 100px; padding: 4px 4px 4px 16px; display: inline-flex; align-items: center; gap: 8px; margin-bottom: 32px; font-size: 14px; font-weight: 500; }
          .avatar { width: 20px; height: 20px; background: #1a73e8; color: white; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; }
          .scopes-box { text-align: left; background: #f8f9fa; border-radius: 8px; padding: 16px; margin-bottom: 32px; border: 1px solid #f1f3f4; }
          .scope-item { display: flex; gap: 12px; margin-bottom: 12px; font-size: 14px; line-height: 1.4; }
          .scope-item:last-child { margin-bottom: 0; }
          .scope-icon { color: #5f6368; flex-shrink: 0; }
          .actions { display: flex; justify-content: space-between; align-items: center; margin-top: 40px; }
          button { font-family: 'Google Sans', sans-serif; font-size: 14px; font-weight: 500; border-radius: 4px; padding: 10px 24px; cursor: pointer; transition: background 0.2s; border: none; }
          .btn-deny { background: transparent; color: #1a73e8; }
          .btn-deny:hover { background: #f6fafe; }
          .btn-approve { background: #1a73e8; color: #fff; }
          .btn-approve:hover { background: #1b66c9; box-shadow: 0 1px 2px 0 rgba(60,64,67,0.302), 0 1px 3px 1px rgba(60,64,67,0.149); }
          .footer { font-size: 12px; color: #70757a; margin-top: 32px; line-height: 1.5; }
          input { width: 100%; padding: 13px 15px; border: 1px solid #dadce0; border-radius: 4px; font-size: 16px; box-sizing: border-box; font-family: inherit; margin-bottom: 16px; }
          input:focus { border: 2px solid #1a73e8; padding: 12px 14px; outline: none; }
          .btn-secondary { background: transparent; color: #1a73e8; text-decoration: none; font-size: 14px; }
        </style>
    </head>
""".trimIndent()

fun consentPage(client: OAuthClient, request: AuthorizationRequest, user: OAuthUser, accessToken: String): String = """
    <!doctype html>
    <html lang="id">
      ${head("Izinkan ${escapeHtml(client.clientName)}")}
      <body>
        <div class="card">
          <div class="logo"><div class="logo-dot"></div>UMKMShop</div>
          <h1>Berikan izin?</h1>
          <div class="subtitle">Aplikasi <strong>${escapeHtml(client.clientName)}</strong> ingin mengakses akun Anda</div>
          
          <div class="user-box">
            <span>${escapeHtml(user.email)}</span>
            <div class="avatar">${escapeHtml(user.name.take(1))}</div>
          </div>

          <div class="scopes-box">
            <div class="scope-item">
              <span class="scope-icon">👤</span>
              <div>Mengetahui nama dan foto profil Anda</div>
            </div>
            <div class="scope-item">
              <span class="scope-icon">📧</span>
              <div>Melihat alamat email utama Anda</div>
            </div>
          </div>

          <form method="post" action="/oauth/authorize">
            ${hidden("oauth_response_type", request.responseType)}
            ${hidden("oauth_client_id", request.clientId)}
            ${hidden("oauth_redirect_uri", request.redirectUri)}
            ${hidden("oauth_scope", request.scope)}
            ${hidden("oauth_state", request.state.orEmpty())}
            ${hidden("oauth_code_challenge", request.codeChallenge.orEmpty())}
            ${hidden("oauth_code_challenge_method", request.codeChallengeMethod.orEmpty())}
            ${hidden("oauth_nonce", request.nonce.orEmpty())}
            ${hidden("access_token", accessToken)}
            
            <div class="actions">
              <button type="submit" name="approve" value="false" class="btn-deny">Batal</button>
              <button type="submit" name="approve" value="true" class="btn-approve">Izinkan</button>
            </div>
          </form>
        </div>
      </body>
    </html>
""".trimIndent()

fun loginPage(client: OAuthClient, request: AuthorizationRequest, error: String? = null): String = """
    <!doctype html>
    <html lang="id">
      ${head("Login - UMKMShop")}
      <body>
        <div class="card">
          <form action="/oauth/login" method="POST">
            <div class="logo"><div class="logo-dot"></div>UMKMShop</div>
            <h1>Login</h1>
            <div class="subtitle">Lanjutkan ke ${escapeHtml(client.clientName)}</div>
            
            ${if (error != null) "<div style='color: #d93025; text-align: left; margin-bottom: 16px; font-size: 14px;'>$error</div>" else ""}

            ${hidden("oauth_response_type", request.responseType)}
            ${hidden("oauth_client_id", request.clientId)}
            ${hidden("oauth_redirect_uri", request.redirectUri)}
            ${hidden("oauth_scope", request.scope)}
            ${hidden("oauth_state", request.state.orEmpty())}
            ${hidden("oauth_code_challenge", request.codeChallenge.orEmpty())}
            ${hidden("oauth_code_challenge_method", request.codeChallengeMethod.orEmpty())}
            ${hidden("oauth_nonce", request.nonce.orEmpty())}

            <div class="input-container">
              <input type="email" name="email" placeholder="Email" required autofocus>
              <input type="password" name="password" placeholder="Password" required>
            </div>
            
            <div class="actions">
              <a href="/oauth/signup?client_id=${request.clientId}&redirect_uri=${URLEncoder.encode(request.redirectUri, "UTF-8")}&response_type=${request.responseType}&scope=${URLEncoder.encode(request.scope, "UTF-8")}&code_challenge=${request.codeChallenge ?: ""}&code_challenge_method=${request.codeChallengeMethod ?: ""}" class="btn-secondary">Buat akun</a>
              <button type="submit" class="btn-approve">Berikutnya</button>
            </div>
          </form>
        </div>
      </body>
    </html>
""".trimIndent()

fun signupPage(client: OAuthClient, request: AuthorizationRequest): String = """
    <!doctype html>
    <html lang="id">
      ${head("Buat Akun - UMKMShop")}
      <body>
        <div class="card">
          <form action="/oauth/signup" method="POST">
            <div class="logo"><div class="logo-dot"></div>UMKMShop</div>
            <h1>Buat Akun</h1>
            <div class="subtitle">Daftar untuk mengakses ${escapeHtml(client.clientName)}</div>
            
            ${hidden("oauth_response_type", request.responseType)}
            ${hidden("oauth_client_id", request.clientId)}
            ${hidden("oauth_redirect_uri", request.redirectUri)}
            ${hidden("oauth_scope", request.scope)}
            ${hidden("oauth_state", request.state.orEmpty())}
            ${hidden("oauth_code_challenge", request.codeChallenge.orEmpty())}
            ${hidden("oauth_code_challenge_method", request.codeChallengeMethod.orEmpty())}
            ${hidden("oauth_nonce", request.nonce.orEmpty())}

            <div class="input-container">
              <input type="text" name="name" placeholder="Nama Lengkap" required autofocus>
              <input type="email" name="email" placeholder="Email" required>
              <input type="password" name="password" placeholder="Password" required>
            </div>

            <div class="actions">
              <a href="/oauth/authorize?client_id=${request.clientId}&redirect_uri=${URLEncoder.encode(request.redirectUri, "UTF-8")}&response_type=${request.responseType}&scope=${URLEncoder.encode(request.scope, "UTF-8")}&code_challenge=${request.codeChallenge ?: ""}&code_challenge_method=${request.codeChallengeMethod ?: ""}" class="btn-secondary">Sudah punya akun?</a>
              <button type="submit" class="btn-approve">Daftar</button>
            </div>
          </form>
        </div>
      </body>
    </html>
""".trimIndent()
