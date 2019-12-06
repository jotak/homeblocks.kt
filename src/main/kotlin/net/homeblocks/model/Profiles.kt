package net.homeblocks.model

import io.vertx.core.json.JsonObject

fun notFound404Profile(): JsonObject {
  val page = Page(listOf(
    MainBlock.build(0, 0, null),
    NoteBlock.build("<h3>404,<br/> Blocks not found!</h3><br/>Oops, looks like you entered a wrong URL", -1, -1, ""),
    LinksBlock.build(listOf(Link("homeblocks.net", "https://www.homeblocks.net/#/u", "Start page")), 1, 1, "Try here")
  ))
  return JsonObject().put("title", "404").put("page", page.toJson())
}

fun loginProfile(authProvider: List<Pair<String, String>>): JsonObject {
  val page = Page(listOf(
    LinksBlock.build(authProvider.map { Link(it.first, it.second, it.first) }, 0, 0, "Login"),
    NoteBlock.build("<h3>Welcome to Homeblocks.net</h3><br/>Build your homepage, block after block!", -1, -1, "")
  ))
  return JsonObject().put("title", "login").put("page", page.toJson())
}

fun singleBlockLoginProfile(authProvider: List<Pair<String, String>>): JsonObject {
  val page = Page(listOf(
    LinksBlock.build(authProvider.map { Link(it.first, it.second, it.first) }, 0, 0, "Login")
  ))
  return JsonObject().put("title", "login").put("page", page.toJson())
}

fun userProfiles(refUser: String, profiles: List<String>, logged: String?): JsonObject {
  val page = Page(listOf(
    MainBlock.build(0, 0, null),
    LinksBlock.build(profiles.map { Link(it, "#/u/$refUser/$it", "") }, 1, 0, "Profiles")
  ))
  val obj = JsonObject().put("title", "$refUser's place").put("page", page.toJson()).put("refUser", refUser)
  if (logged != null) {
    obj.put("logged", logged)
  }
  return obj
}

fun pageProfile(refUser: String, profile: String, page: Page, logged: String?): JsonObject {
  val obj = JsonObject().put("title", "$refUser's $profile").put("page", page.toJson()).put("refUser", refUser).put("profile", profile)
  if (logged != null) {
    obj.put("logged", logged)
  }
  return obj
}
