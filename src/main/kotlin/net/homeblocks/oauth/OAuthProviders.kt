package net.homeblocks.oauth

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.kotlin.core.file.readFileAwait
import io.vertx.kotlin.ext.auth.oauth2.fetchAwait
import java.io.File

sealed class OAuthProvider(val name: String, val displayName: String, val redirectURI: String, val oAuth2: OAuth2Auth) {
  abstract fun authorizeURL(state: String): String
  abstract suspend fun getUID(user: AccessToken): String
}

class GithubOAuth(name: String, displayName: String, redirectURI: String, oAuth2: OAuth2Auth) : OAuthProvider(name, displayName, redirectURI, oAuth2) {
  override fun authorizeURL(state: String): String {
    return oAuth2.authorizeURL(JsonObject().put("state", state))
  }

  override suspend fun getUID(user: AccessToken): String {
    val obj = user.fetchAwait(HttpMethod.GET, "https://api.github.com/user", user.principal(), Buffer.buffer()).jsonObject()
    return obj.getInteger("id").toString()
  }
}

suspend fun createProvider(file: File, vertx: Vertx, fs: FileSystem): OAuthProvider? {
  runCatching {
    val descriptor = fs.readFileAwait(file.absolutePath).toJsonObject()
    val type = descriptor.getString("type")
    val name = descriptor.getString("shortName")
    val displayName = descriptor.getString("displayName")
    val redirectURI = descriptor.getString("redirectURI")
    val config = descriptor.getJsonObject("config")
    val oauth2 = OAuth2Auth.create(vertx, OAuth2ClientOptions(config))
    return when(type) {
      "github" -> GithubOAuth(name, displayName, redirectURI, oauth2)
      else -> {
        println("Unrecognized OAuth provider: $type")
        null
      }
    }
  }.onFailure {
    println("Can't read oauth2 config file $file, skipping")
  }
  return null
}

suspend fun getOAuthProviders(fsRoot: File, vertx: Vertx): List<OAuthProvider> {
  val fs = vertx.fileSystem()
  val dir = fsRoot.resolve("oauth")
  return dir.list()?.toList()?.map { dir.resolve(it) }
          ?.filter { it.isFile }
          ?.mapNotNull { createProvider(it, vertx, fs) }
          ?: emptyList()
}
