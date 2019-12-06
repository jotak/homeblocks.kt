package net.homeblocks.server

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.auth.authenticateAwait
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.homeblocks.model.*
import net.homeblocks.oauth.OAuthProvider
import net.homeblocks.services.ProfileService
import net.homeblocks.services.UserService
import java.util.*
import kotlin.collections.HashMap

fun error(ctx: RoutingContext, errorCode: Int, msg: String?) {
  ctx.response().statusCode = errorCode
  ctx.response().end(msg)
}

fun getLoggedUser(ctx: RoutingContext): HttpUser? {
  return ctx.session()?.get("user")
}

fun isValidLoggedUser(ctx: RoutingContext, user: UserInfo): Boolean {
  val logged = getLoggedUser(ctx)
  if (logged != null) {
    if (user.intIdx == logged.userInfo.intIdx) {
      return !logged.token.expired()
    }
  }
  return false
}

fun updateLoggedUser(ctx: RoutingContext, newUser: UserInfo) {
  val logged = getLoggedUser(ctx)
  if (logged != null) {
    if (newUser.intIdx == logged.userInfo.intIdx) {
      ctx.session()?.put("user", HttpUser(logged.token, newUser, logged.stateToken))
    }
  }
}

data class HttpUser(val token: AccessToken, val userInfo: UserInfo, val stateToken: String)

class Routes private constructor(val vertx: Vertx, private val userService: UserService, private val profileService: ProfileService, private val oauthProviders: List<OAuthProvider>) {
  private val tmpStates = HashMap<String, JsonObject>()

  companion object {
    fun create(vertx: Vertx, userService: UserService, profileService: ProfileService, oauthProviders: List<OAuthProvider>): Router {
      val routes = Routes(vertx, userService, profileService, oauthProviders)
      val router = Router.router(vertx)
      val store = LocalSessionStore.create(vertx)
      router.route().handler(CookieHandler.create())
      router.route().handler(SessionHandler.create(store))

      // Login endpoints
      router.get("/api/login").handler { routes.getLoginPage(it) }
      router.post("/api/login").handler { routes.postLoginPage(it) }
      router.get("/api/logout").handler { routes.logout(it) }
      router.get("/api/logged").handler { routes.getLogged(it) }

      // Oauth endpoints
      oauthProviders.forEach { prov -> router.get("/oauthclbk-" + prov.name).handler {
        GlobalScope.launch(vertx.dispatcher()) {
          routes.providerCallback(it, prov)
        }
      }}

      // API endpoints
      router.get("/api/user/:user").handler { routes.getUser(it) }
      router.get("/api/user/:user/profile/:name").handler { ctx -> GlobalScope.launch(vertx.dispatcher()) {
        routes.getProfile(ctx)
      }}
      router.put("/api/user/:user/profile/:name").handler { ctx -> GlobalScope.launch(vertx.dispatcher()) {
        routes.createProfile(ctx)
      }}
      router.post("/api/user/:user/profile/:name").handler { ctx -> GlobalScope.launch(vertx.dispatcher()) {
        routes.updateProfile(ctx)
      }}
      router.put("/api/alias/:alias").handler { ctx -> GlobalScope.launch(vertx.dispatcher()) {
        routes.setAlias(ctx)
      }}

      // Serve static
      val stcHandler = StaticHandler.create("public")
      router.route("/*").handler {
        stcHandler.handle(it)
      }

      return router
    }
  }

  private fun buildLoginPageInfo(json: JsonObject): List<Pair<String, String>> {
    val state = UUID.randomUUID().toString()
    tmpStates[state] = json
    return oauthProviders.map {
      val authorizationUrl = it.authorizeURL(state)
      Pair("Login with " + it.displayName, authorizationUrl)
    }
  }

  fun getLoginPage(ctx: RoutingContext) {
    ctx.response().end(loginProfile(buildLoginPageInfo(JsonObject())).toString())
  }

  fun postLoginPage(ctx: RoutingContext) {
    kotlin.runCatching {
      ctx.request().bodyHandler {
        ctx.response().end(singleBlockLoginProfile(buildLoginPageInfo(it.toJsonObject())).toString())
      }
    }.onFailure {
      error(ctx, 500, it.message)
      it.printStackTrace()
    }
  }

  fun logout(ctx: RoutingContext) {
    val user = getLoggedUser(ctx)
    if (user != null) {
      ctx.session().remove<Any>("user")
    }
    ctx.response().end()
  }

  private fun popState(state: String): JsonObject? {
    val obj = tmpStates[state]
    if (obj != null) {
      tmpStates.remove(state)
      return obj
    }
    return null
  }

  fun getLogged(ctx: RoutingContext) {
    val logged = getLoggedUser(ctx)
    if (logged != null) {
      val json = popState(logged.stateToken)
      if (json != null) {
        json.put("logged", logged.userInfo.name)
        ctx.response().end(json.toString())
      } else {
        ctx.response().end(JsonObject().put("logged", logged.userInfo.name).toString())
      }
    } else {
      ctx.response().end()
    }
  }

  fun getUser(ctx: RoutingContext) {
    val res = ctx.response()
    val user = ctx.request().getParam("user")
    if (user != null) {
      val userInfo = userService.findByAlias(user)
      if (userInfo != null) {
        val logged = getLoggedUser(ctx)?.userInfo?.name
        kotlin.runCatching {
          val profiles = profileService.list(userInfo.intIdx)
          res.end(userProfiles(user, profiles, logged).toString())
        }.onFailure {
          res.end(notFound404Profile().toString())
          it.printStackTrace()
        }
      } else {
        res.end(notFound404Profile().toString())
      }
    }
  }

  suspend fun getProfile(ctx: RoutingContext) {
    val res = ctx.response()
    val user = ctx.request().getParam("user")
    val profile = ctx.request().getParam("name")
    if (user != null && profile != null) {
      val userInfo = userService.findByAlias(user)
      if (userInfo != null) {
        val logged = getLoggedUser(ctx)?.userInfo?.name
          kotlin.runCatching {
            val page = profileService.load(userInfo.intIdx, profile)
            res.end(pageProfile(user, profile, page, logged).toString())
          }.onFailure {
            res.end(notFound404Profile().toString())
            it.printStackTrace()
          }
      } else {
        res.end(notFound404Profile().toString())
      }
    }
  }

  suspend fun createProfile(ctx: RoutingContext) {
    val res = ctx.response()
    val user = ctx.request().getParam("user")
    val profile = ctx.request().getParam("name")
    if (user != null && profile != null) {
      val userInfo = userService.findByAlias(user)
      if (userInfo != null) {
        // Is still logged?
        if (isValidLoggedUser(ctx, userInfo)) {
          val logged = getLoggedUser(ctx)?.userInfo?.name
          kotlin.runCatching {
            val page = profileService.createEmpty(userInfo.intIdx, profile)
            res.end(pageProfile(user, profile, page, logged).toString())
          }.onFailure {
            error(ctx, 500, it.message)
            it.printStackTrace()
          }
        } else {
          error(ctx, 403, "You must log in")
        }
      } else {
        res.end(notFound404Profile().toString())
      }
    }
  }

  suspend fun updateProfile(ctx: RoutingContext) {
    val res = ctx.response()
    val user = ctx.request().getParam("user")
    val profile = ctx.request().getParam("name")
    if (user != null && profile != null) {
      val userInfo = userService.findByAlias(user)
      if (userInfo != null) {
        // Is still logged?
        if (isValidLoggedUser(ctx, userInfo)) {
          kotlin.runCatching {
            ctx.request().bodyHandler {
              GlobalScope.launch(vertx.dispatcher()) {
                profileService.update(userInfo.intIdx, profile, Page.fromJson(it.toJsonObject()))
                res.end()
              }
            }
          }.onFailure {
            error(ctx, 500, it.message)
            it.printStackTrace()
          }
        } else {
          error(ctx, 403, "You must log in")
        }
      } else {
        error(ctx, 404, "User not found")
      }
    }
  }

  suspend fun setAlias(ctx: RoutingContext) {
    val res = ctx.response()
    val alias = ctx.request().getParam("alias")
    if (alias != null) {
      val userInfo = getLoggedUser(ctx)?.userInfo
      if (userInfo != null) {
        // Is still logged?
        if (isValidLoggedUser(ctx, userInfo)) {
          kotlin.runCatching {
            val newUser = userService.saveAlias(userInfo.intIdx, alias)
            // Update logged user
            updateLoggedUser(ctx, newUser)
            res.end((newUser.name == alias).toString())
          }.onFailure {
            error(ctx, 500, it.message)
            it.printStackTrace()
          }
        } else {
          error(ctx, 403, "You must log in")
        }
      } else {
        res.end(notFound404Profile().toString())
      }
    }
  }

  suspend fun providerCallback(ctx: RoutingContext, prov: OAuthProvider) {
    val state = ctx.request().getParam("state")
    val code = ctx.request().getParam("code")
    if (state != null && code != null) {
      if (!tmpStates.containsKey(state)) {
        error(ctx, 403, "Invalid state")
      } else {
        runCatching {
          val user = prov.oAuth2.authenticateAwait(JsonObject().put("code", code).put("redirect_uri", prov.redirectURI)) as AccessToken
          val id = prov.getUID(user)
          val userInfo = userService.findOrCreate(prov.name, id)
          ctx.session().put("user", HttpUser(user, userInfo, state))
          ctx.reroute("/reroute.html")
        }.onFailure {
          error(ctx, 403, it.message)
          it.printStackTrace()
        }
      }
    } else {
      error(ctx, 403, "Authentication failure")
    }
  }
}

