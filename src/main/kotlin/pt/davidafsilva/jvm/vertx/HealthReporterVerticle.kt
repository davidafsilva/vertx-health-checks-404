package pt.davidafsilva.jvm.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.openapi.RouterBuilder
import java.lang.System.Logger.Level.ERROR
import java.lang.System.Logger.Level.INFO

sealed class HealthReporterVerticle : AbstractVerticle() {
    protected val logger: System.Logger = System.getLogger(javaClass.canonicalName)

    override fun start(startPromise: Promise<Void>) {
        val healthChecker = HealthCheckHandler.create(vertx)
            .register("dummy") { p -> p.complete(Status.OK()) }
        createRouter(healthChecker)
            .onFailure(startPromise::fail)
            .onSuccess { router -> startHttpServer(startPromise, router) }
    }

    private fun startHttpServer(startPromise: Promise<Void>, apiRouter: Router) {
        val rootRouter = Router.router(vertx).apply {
            // more sub-routers here...
            route("/notroot/*").subRouter(apiRouter) // just for testing and problem demonstration
            // redirect everything else to the API router
            route("/*").subRouter(apiRouter)
        }

        val serverOptions = HttpServerOptions().apply { port = 0 }
        vertx.createHttpServer(serverOptions)
            .requestHandler(rootRouter)
            .exceptionHandler { e -> logger.log(ERROR, "error while handling a request", e) }
            .listen { result ->
                if (result.succeeded()) {
                    val address = "${serverOptions.host}:${result.result().actualPort()}"
                    logger.log(INFO, "${javaClass.simpleName} server deployed at $address")
                    startPromise.complete()
                } else startPromise.fail(result.cause())
            }
    }

    abstract fun createRouter(healthHandler: Handler<RoutingContext>): Future<Router>
}

class SimpleHealthReporterVerticle : HealthReporterVerticle() {

    override fun createRouter(healthHandler: Handler<RoutingContext>): Future<Router> {
        val router = Router.router(vertx)
        router.get("/health").handler(healthHandler)
        return succeededFuture(router)
    }
}

class OpenApiHealthReporterVerticle : HealthReporterVerticle() {
    companion object {
        private const val HEALTH_OPERATION_ID = "get_health_status"
    }

    override fun createRouter(healthHandler: Handler<RoutingContext>): Future<Router> = future { promise ->
        RouterBuilder.create(vertx, "spec.yaml")
            .onFailure { e ->
                logger.log(ERROR, "unable to setup router", e)
                promise.fail(e)
            }
            .onSuccess { routerBuilder ->
                routerBuilder
                    .operation(HEALTH_OPERATION_ID)
                    .handler(healthHandler)
                promise.complete(routerBuilder.createRouter())
            }
    }
}
