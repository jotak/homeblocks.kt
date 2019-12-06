package net.homeblocks.server

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpServerOptions
import kotlinx.coroutines.runBlocking
import net.homeblocks.oauth.getOAuthProviders
import net.homeblocks.services.ProfileService
import net.homeblocks.services.UserService
import kotlin.concurrent.thread

// const val securePort = 443
// const val insecurePort = 80
const val securePort = 8000
const val insecurePort = 8001

class Server: AbstractVerticle() {
  override fun start(startFuture: Future<Void>) {
    thread {
      runBlocking {
        val userService = UserService.create(vertx, "..")
        val profileService = ProfileService(userService, vertx)
        initServer(userService, profileService, startFuture)
      }
    }
  }

  private suspend fun initServer(userService: UserService, profileService: ProfileService, startFuture: Future<Void>) {
    val options = HttpServerOptions()
//        .setSsl(true).setPemKeyCertOptions(PemKeyCertOptions()
//        .setKeyPath("/etc/letsencrypt/live/www.homeblocks.net/privkey.pem")
//        .setCertPath("/etc/letsencrypt/live/www.homeblocks.net/fullchain.pem")
//    )
    val oAuthProviders = getOAuthProviders(userService.fsRoot, vertx)
    val router = Routes.create(vertx, userService, profileService, oAuthProviders)

    vertx.createHttpServer(options).requestHandler(router).listen(securePort) { http ->
      if (http.succeeded()) {
        startFuture.complete()
        println("HTTPS server started on port $securePort")
      } else {
        startFuture.fail(http.cause())
      }
    }

    // Keep listening on 80, and redirect
    vertx.createHttpServer().requestHandler {
        it.response()
            .setStatusCode(301)
            .putHeader("Location", it.absoluteURI().replace("http", "https"))
            .end()
    }.listen(insecurePort) { http ->
      if (http.succeeded()) {
        println("HTTP server started, redirecting to HTTPS")
      } else {
        println(http.cause())
      }
    }
  }
}
