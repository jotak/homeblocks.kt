package net.homeblocks.model

import io.vertx.core.json.JsonObject

data class UserInfo(val prov: String, val provUId: String, val intIdx: Int, val name: String) {
  fun toJson(): JsonObject {
    return JsonObject().put("prov", prov).put("provUId", provUId).put("intIdx", intIdx).put("name", name)
  }

  companion object {
    fun fromJson(json: JsonObject): UserInfo {
      return UserInfo(json.getString("prov"), json.getString("provUId"), json.getInteger("intIdx"), json.getString("name"))
    }
  }
}
