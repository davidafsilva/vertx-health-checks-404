package pt.davidafsilva.jvm.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture.all
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory

object Application {
    private val logger = LoggerFactory.getLogger(Application::class.java)

    fun start() {
        val vertx = Vertx.vertx()
        all(
            vertx.deploy(SimpleHealthReporterVerticle()),
            vertx.deploy(OpenApiHealthReporterVerticle()),
        ).onSuccess {
            logger.info("application successfully started")
        }.onFailure { e ->
            logger.error("failed to start application", e)
        }
    }

    private fun Vertx.deploy(verticle: AbstractVerticle): Future<Unit> = future { promise ->
        deployVerticle(verticle) { result ->
            if (result.succeeded()) promise.complete(Unit)
            else promise.fail(result.cause())
        }
    }
}

fun main() {
    Application.start()
}
