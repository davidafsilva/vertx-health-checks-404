
# vertx-health-checks-404

This repository aims to provide a simplified version of an issue that is 
occurring when updating to Vert.x `3.9.13` from `3.7.1` on a long-running
private project.
The project relies on `vertx-health-checks` to provide statuses reports
through a couple of endpoints.

## The Symptom

When a request that was targetting an handler `H` - where `H` is the handler 
implemented by `HealthCheckHandlerImpl` - were issued, depending on how 
the routing was mounted, clients would either get a `404` or a correct response.

### The Problematic Setup

The setup that enables a consistent replication of the issue is detailed
in this repository. In particular, mounting the router that contains an 
endpoint associated with the health check handler mounted as a sub-router in a 
root path (`/`), as exemplified below.

```kotlin
val healthChecker = HealthCheckHandler.create(vertx)
   .register("dummy") { p -> p.complete(Status.OK()) }

val apiRouter = Router.router(vertx)
apiRouter.get("/health").handler(healthChecker)
// other routes..

val rootRouter = Router.router(vertx)
    // more sub-routers here...
    .mountSubRouter("/nonroot", apiRouter) // just for demonstration
    // redirect everything else to the API router
    .mountSubRouter("/", apiRouter)
```

### The Cause

The cause of the issue, **with the above setup**, seems to be the way that
sub-routers are handled within [HealthCheckHandlerImpl](https://github.com/vert-x3/vertx-health-check/blob/2b33c1d2ec7ed5bf48e2aa19d551ae02d72d0a5e/src/main/java/io/vertx/ext/healthchecks/impl/HealthCheckHandlerImpl.java#L68-L79).
<img src="https://i.imgur.com/tFRoSj6.png">

Assuming a request to `/health`, those 4 variables defined between 
L62-L64 will be assigned with:
```java
String path = "/health"
String mount = "/"
String route = "/health"
```

On L71, because `path` starts with `/`, `path` will be overridden to `health`:
```java
path = "health"
```

On L78, because `path` does not start with `/health` - its value is now `health`
and not `/health`, `id` will be set to `health`:
```java
id = "health"
```

`id` will then be handed over to actual [health check implementation](https://github.com/vert-x3/vertx-health-check/blob/2b33c1d2ec7ed5bf48e2aa19d551ae02d72d0a5e/src/main/java/io/vertx/ext/healthchecks/impl/HealthChecksImpl.java#L95-L127)
as the name of the check for which we're retrieving the status (vs all of them):
<img src="https://i.imgur.com/EchgkAY.png" />

On L65, `segments` will be assigned to `health`, thus it will be an array of a 
single element:
```java
String[] segments = {"health"}
```

On L72, we will try to lookup the check (segment) within the (root) check 
registry, which can lead to two possible outcomes<sup>1</sup>:
1. `null`: there are no checks registered with the `health` name
2. `!= null`: there is a check registered with the `health` name

In most cases we will fall into 1 which, as depicted in L75, will cause the 
procedure to fail with `Not Found`. Later it is [translated to a 404](https://github.com/vert-x3/vertx-health-check/blob/2b33c1d2ec7ed5bf48e2aa19d551ae02d72d0a5e/src/main/java/io/vertx/ext/healthchecks/impl/HealthCheckHandlerImpl.java#L116-L117).  
If by any chance we happen to have registered a check with `health` as its name,
the outcome of the health check procedure will only account for that particular
check instead of all of them.

<sup>
[1] Assuming we have registered at least one check, otherwise it's not very
helpful, is it?
</sup>

## Replicating the issue

To replicate the issue:
1. Clone this repository 
2. Run the underlying application
```shell
./gradlew run
```
3. Request`/health` to one of the exposed ports<sup>1</sup>
```shell
❯ http :59238/health
HTTP/1.1 404 Not Found
content-length: 24
content-type: application/json;charset=UTF-8
{
    "message": "Not found"
}
```
4. Optionally, you can issue a request to `/nonroot/health` which was mounted 
   as a sub-router as well, but on `/nonroot` instead of `/` and it works just
   fine
```shell
❯ http :59238/notroot/health
HTTP/1.1 200 OK
content-length: 56
content-type: application/json;charset=UTF-8

{
    "checks": [
        {
            "id": "dummy",
            "status": "UP"
        }
    ],
    "outcome": "UP"
}
```

<sup>
[1] There's an implementation of a server which setups the API router
    manually and other through OpenAPI
</sup>
