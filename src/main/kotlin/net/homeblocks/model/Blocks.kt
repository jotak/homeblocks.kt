package net.homeblocks.model

import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject

data class BaseBlock(val type: String, val posx: Int, val posy: Int, val title: String?) {
  fun toJson(): JsonObject {
    return JsonObject().put("type", type).put("posx", posx).put("posy", posy).put("title", title)
  }
}

interface IBlock { fun toJson(): JsonObject }
sealed class Block : IBlock

fun blockFromJson(json: JsonObject): Block {
  return when (val type = json.getString("type")) {
    "audio" -> AudioBlock.fromJson(json)
    "image" -> ImageBlock.fromJson(json)
    "links" -> LinksBlock.fromJson(json)
    "list" -> ListBlock.fromJson(json)
    "main" -> MainBlock.fromJson(json)
    "note" -> NoteBlock.fromJson(json)
    "video" -> VideoBlock.fromJson(json)
    else -> throw DecodeException("Unknown type for block: $type")
  }
}

data class Link(val title: String, val url: String, val description: String?) {
  fun toJson(): JsonObject {
    return JsonObject().put("title", title).put("url", url).put("description", description)
  }

  companion object {
    fun fromJson(json: JsonObject): Link {
      return Link(json.getString("title"), json.getString("url"), json.getString("description"))
    }
    fun linksFromJson(json: JsonObject): List<Link> {
      return json.getJsonArray("links").map { when(it) {
        is JsonObject -> fromJson(it)
        else -> throw DecodeException("Expected JsonObject in Links")
      }}
    }
  }
}

data class AudioBlock(val block: BaseBlock, val links: List<Link>) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("links", links.map { it.toJson() })
  }

  companion object {
    fun build(links: List<Link>, posx: Int, posy: Int, title: String?): AudioBlock {
      return AudioBlock(BaseBlock("audio", posx, posy, title), links)
    }
    fun fromJson(json: JsonObject): AudioBlock {
      return build(Link.linksFromJson(json), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class ImageBlock(val block: BaseBlock, val links: List<Link>) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("links", links.map { it.toJson() })
  }

  companion object {
    fun build(links: List<Link>, posx: Int, posy: Int, title: String?): ImageBlock {
      return ImageBlock(BaseBlock("image", posx, posy, title), links)
    }
    fun fromJson(json: JsonObject): ImageBlock {
      return build(Link.linksFromJson(json), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class LinksBlock(val block: BaseBlock, val links: List<Link>) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("links", links.map { it.toJson() })
  }

  companion object {
    fun build(links: List<Link>, posx: Int, posy: Int, title: String?): LinksBlock {
      return LinksBlock(BaseBlock("links", posx, posy, title), links)
    }
    fun fromJson(json: JsonObject): LinksBlock {
      return build(Link.linksFromJson(json), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class ListItem(val value: String) {
  fun toJson(): JsonObject {
    return JsonObject().put("value", value)
  }

  companion object {
    fun fromJson(json: JsonObject): ListItem {
      return ListItem(json.getString("value"))
    }
    fun itemsFromJson(json: JsonObject): List<ListItem> {
      return json.getJsonArray("list").map { when(it) {
        is JsonObject -> fromJson(it)
        else -> throw DecodeException("Expected JsonObject in List")
      }}
    }
  }
}

data class ListBlock(val block: BaseBlock, val list: List<ListItem>) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("list", list.map { it.toJson() })
  }

  companion object {
    fun build(list: List<ListItem>, posx: Int, posy: Int, title: String?): ListBlock {
      return ListBlock(BaseBlock("list", posx, posy, title), list)
    }
    fun fromJson(json: JsonObject): ListBlock {
      return build(ListItem.itemsFromJson(json), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class MainBlock(val block: BaseBlock) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson()
  }

  companion object {
    fun build(posx: Int, posy: Int, title: String?): MainBlock {
      return MainBlock(BaseBlock("main", posx, posy, title))
    }
    fun fromJson(json: JsonObject): MainBlock {
      return build(json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class NoteBlock(val block: BaseBlock, val note: String) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("note", note)
  }

  companion object {
    fun build(note: String, posx: Int, posy: Int, title: String?): NoteBlock {
      return NoteBlock(BaseBlock("note", posx, posy, title), note)
    }
    fun fromJson(json: JsonObject): NoteBlock {
      return build(json.getString("note"), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}

data class VideoBlock(val block: BaseBlock, val links: List<Link>) : Block() {
  override fun toJson(): JsonObject {
    return block.toJson().put("links", links.map { it.toJson() })
  }

  companion object {
    fun build(links: List<Link>, posx: Int, posy: Int, title: String?): VideoBlock {
      return VideoBlock(BaseBlock("video", posx, posy, title), links)
    }
    fun fromJson(json: JsonObject): VideoBlock {
      return build(Link.linksFromJson(json), json.getInteger("posx"), json.getInteger("posy"), json.getString("title"))
    }
  }
}
