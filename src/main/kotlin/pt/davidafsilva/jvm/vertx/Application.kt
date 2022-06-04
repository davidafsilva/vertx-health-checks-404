package pt.davidafsilva.jvm.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture.all
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import java.lang.System.Logger.Level.ERROR
import java.lang.System.Logger.Level.INFO

object Application {
    private val logger = System.getLogger(Application::class.java.canonicalName)

    fun start() {
        val vertx = Vertx.vertx()
        all(
            vertx.deploy(SimpleHealthReporterVerticle()),
            vertx.deploy(OpenApiHealthReporterVerticle()),
        ).onSuccess {
            logger.log(INFO, "application successfully started")
        }.onFailure { e ->
            logger.log(ERROR, "failed to start application", e)
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
