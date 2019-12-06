package net.homeblocks.model

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject

data class Page(val blocks: List<Block>) {
  fun toJson(): JsonObject {
    return JsonObject().put("blocks", blocks.map { it.toJson() })
  }

  companion object {
    fun fromJson(json: JsonObject): Page {
      return Page(json.getJsonArray("blocks").map {
        when(it) {
          is JsonObject -> blockFromJson(it)
          else -> throw DecodeException("Excepted Block JsonObject in Page")
        }
      })
    }
  }
}

fun emptyPage(): Page = Page(listOf(MainBlock.build(0, 0, null)))
