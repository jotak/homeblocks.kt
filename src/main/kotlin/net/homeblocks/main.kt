package net.homeblocks

import io.vertx.core.Vertx
import net.homeblocks.server.Server

/**
 * @author Joel Takvorian
 */


fun main() {
  Vertx.vertx().deployVerticle(Server())
}
