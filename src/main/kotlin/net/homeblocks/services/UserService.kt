package net.homeblocks.services

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.file.readFileAwait
import io.vertx.kotlin.core.file.writeFileAwait
import net.homeblocks.model.UserInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class UserService private constructor(
    vertx: Vertx,
    strRoot: String
) {
  private val fs = vertx.fileSystem()
  val fsRoot = File(strRoot)
  private val providerKey2 = { p: String, id: String -> "$p-$id" }
  private val providerKey = { userInfo: UserInfo -> providerKey2(userInfo.prov, userInfo.provUId) }
  private val usersIndex = HashMap<Int, UserInfo>()
  private val aliasUsersIndex = HashMap<String, UserInfo>()
  private val providerUsersIndex = HashMap<String, UserInfo>()
  private val maxIdx = AtomicInteger(0)
  val userDir = fsRoot.resolve("users")
  private val indexFile = userDir.resolve("_index.json")

  companion object {
    suspend fun create(vertx: Vertx, strRoot: String): UserService {
      return UserService(vertx, strRoot).also {
        it.init()
      }
    }
  }

  private suspend fun init() {
    userDir.mkdirs()

    // Read users index
    if (!indexFile.exists()) {
      indexFile.writeText("[]")
    } else {
      val users = fs.readFileAwait(indexFile.absolutePath).toJsonArray()
      users.forEach {
        when (it) {
          is JsonObject -> {
            val userInfo = UserInfo.fromJson(it)
            val provKey = providerKey(userInfo)
            if (!usersIndex.containsKey(userInfo.intIdx)
                && !aliasUsersIndex.containsKey(userInfo.name)
                && !providerUsersIndex.containsKey(provKey)) {
              usersIndex[userInfo.intIdx] = userInfo
              aliasUsersIndex[userInfo.name] = userInfo
              providerUsersIndex[provKey] = userInfo
            } else {
              println("Cannot load index for user " + userInfo.intIdx + " (" + userInfo.name
                  + "), index or alias already used")
            }
          }
          else -> println("Users index corrupted, object expected but got: $it")
        }
      }
      maxIdx.set(usersIndex.keys.fold(0) { a, b -> if (a > b) a else b })
    }
  }

  private fun isAliasAvailable(userAlias: String): Boolean {
    if (userAlias.startsWith("@user")) {
      // Reserved for internal alias generation
      return false
    }
    return !aliasUsersIndex.containsKey(userAlias)
  }

  private suspend fun writeUsersIndex() {
    val arr = JsonArray(usersIndex.values.map { it.toJson() })
    fs.writeFileAwait(indexFile.absolutePath, arr.toBuffer())
  }

  private suspend fun updateUsersIndex(userInfo: UserInfo): UserInfo {
    val old = usersIndex[userInfo.intIdx]
    if (old != null) {
      aliasUsersIndex.remove(old.name)
      providerUsersIndex.remove(providerKey(old))
    }
    usersIndex[userInfo.intIdx] = userInfo
    aliasUsersIndex[userInfo.name] = userInfo
    providerUsersIndex[providerKey(userInfo)] = userInfo
    writeUsersIndex()
    return userInfo
  }

  suspend fun findOrCreate(provider: String, provUID: String): UserInfo {
    val userInfo = providerUsersIndex[providerKey2(provider, provUID)]
    if (userInfo != null) {
      return userInfo
    }
    val id = maxIdx.addAndGet(1)
    val newUserInfo = UserInfo(provider, provUID, id, "@user$id")
    return updateUsersIndex(newUserInfo)
  }

  suspend fun saveAlias(id: Int, userAlias: String): UserInfo {
    val oldUser = usersIndex[id]
    if (oldUser != null) {
      if (isAliasAvailable(userAlias)) {
        val newUser = UserInfo(oldUser.prov, oldUser.provUId, id, userAlias)
        return updateUsersIndex(newUser)
      }
      return oldUser
    }
    throw Exception("Could not find existing user")
  }

  fun findById(id: Int): UserInfo? {
    return usersIndex[id]
  }

  fun findByAlias(name: String): UserInfo? {
    return aliasUsersIndex[name]
  }
}
