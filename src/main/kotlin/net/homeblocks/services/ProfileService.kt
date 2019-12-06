package net.homeblocks.services

import io.vertx.core.Vertx
import io.vertx.kotlin.core.file.readFileAwait
import io.vertx.kotlin.core.file.writeFileAwait
import net.homeblocks.model.Page
import net.homeblocks.model.emptyPage
import java.lang.Exception

class ProfileService(userService: UserService, vertx: Vertx) {
  private val fs = vertx.fileSystem()
  private val userPath = { userID: Int -> userService.userDir.resolve(userID.toString()) }
  private val profilePath = { userID: Int, profile: String -> userPath(userID).resolve("$profile.json") }

  fun list(userID: Int): List<String> {
    val path = userPath(userID)
    if (path.isDirectory) {
      return path.list()?.toList()?.map { path.resolve(it) }?.filter { it.isFile }?.map { it.nameWithoutExtension }
      ?: emptyList()
    }
    return emptyList()
  }

  suspend fun load(userID: Int, profile: String): Page {
    val path = profilePath(userID, profile)
    if (path.isFile) {
      val pageJson = fs.readFileAwait(path.absolutePath).toJsonObject()
      return Page.fromJson(pageJson)
    }
    throw Exception("Can't load profile: file not found")
  }

  suspend fun createEmpty(userID: Int, profile: String): Page {
    userPath(userID).mkdirs()
    val path = profilePath(userID, profile)
    if (path.exists()) {
      throw Exception("Trying to create '$path', but it already exists")
    }
    val page = emptyPage()
    fs.writeFileAwait(path.absolutePath, page.toJson().toBuffer())
    return page
  }

  suspend fun update(userID: Int, profile: String, page: Page) {
    userPath(userID).mkdirs()
    val path = profilePath(userID, profile)
    if (!path.exists()) {
      throw Exception("Could not retrieve the profile to update")
    }
    fs.writeFileAwait(path.absolutePath, page.toJson().toBuffer())
  }
}
