package pt.davidafsilva.jvm.vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory

sealed class HealthReporterVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun start(startPromise: Promise<Void>) {
        val healthChecker = HealthCheckHandler.create(vertx)
            .register("dummy") { p -> p.complete(Status.OK()) }
        createRouter(healthChecker)
            .onFailure(startPromise::fail)
            .onSuccess { router -> startHttpServer(startPromise, router) }
    }

    private fun startHttpServer(startPromise: Promise<Void>, apiRouter: Router) {
        val rootRouter = Router.router(vertx)
            // more sub-routers here...
            .mountSubRouter("/notroot", apiRouter) // just for testing and problem demonstration
            // redirect everything else to the API router
            .mountSubRouter("/", apiRouter)

        val serverOptions = HttpServerOptions().apply { port = 0 }
        vertx.createHttpServer(serverOptions)
            .requestHandler(rootRouter)
            .exceptionHandler { e -> logger.error("error while handling a request", e) }
            .listen { result ->
                if (result.succeeded()) {
                    logger.info("${javaClass.simpleName} server deployed at ${serverOptions.host}:${result.result().actualPort()}")
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
        OpenAPI3RouterFactory.create(vertx, "/spec.yaml") { routerFactoryResult ->
            if (routerFactoryResult.failed()) {
                promise.fail(routerFactoryResult.cause())
                return@create
            }

            val router = routerFactoryResult.result()
                .addHandlerByOperationId(HEALTH_OPERATION_ID, healthHandler)
                .router
            promise.complete(router)
        }
    }
}
