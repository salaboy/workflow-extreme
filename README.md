# workflow-extreme

This repository includes a set of workflow patterns and examples that shows how to use Dapr Workflow with Spring Boot applications.

The `workflows` application can be started by running the following commands for local development:

```
cd workflows/
mvn spring-boot:test-run
```

## Running with Catalyst:


You can set your Catalyst ENV Variables or set the following Spring Boot Application properties:

In your `application.properties` file:
```
dapr.client.httpEndpoint=<Your Catalyst Project HTTP Endpoint>
dapr.client.grpcEndpoint=<Your Catalyst Project GRPC Endpoint>
dapr.client.apiToken=<Your Catalyst APP ID API Token>
```

Or export the following ENV Variables: 

```
export DAPR_HTTP_ENDPOINT=<Your Catalyst Project HTTP Endpoint>
export DAPR_GRPC_ENDPOINT=<Your Catalyst Project GRPC Endpoint>
export DAPR_API_TOKEN=<Your Catalyst APP ID API Token>
```

Before running: 

```sh
mvn spring-boot:run
```

Alternatively, you can run the JAR file that will be created by running:
```
mvn package -DskipTests
```

And then to run the app: 
```sh
java -jar target/workflow-extreme-1.0.0.jar

```

### Scenario

This example showcase the following example:
- Start Workflow
- First Activity executed
- For each payment item set in the request, create a ChildWorkflow asynchronously with two activities
  - FirstChildActivity -> modify the payload
  - SecondChildActivity -> modify the payload
  - Complete child workflow
  - wait for all the childworkflows to complete and print all the modified payloads.
- For( from 0 to X=10)
    - Wait for Event with 2 seconds timeout, but retry 10 times if the event never arrives
        - Catch TaskCancelledException
        - Execute RetryActivity (to reset state and go back to wait for Event)
    - If the event arrives, break the for loop and move forward
- If the event hasn't arrived after retrying 10 times:
    - Execute CompensationActivity
- Else If the event arrived:
    - Execute NextActivity
- Complete workflow



Once the application is running, you can invoke the endpoint using `cURL` or [`HTTPie`](https://httpie.io/).

```sh
http :8080/start --raw '{"id": "123", "customer": "salaboy", "amount": 10, "paymentItems": [ {"itemName": "test"}, {"itemName": "test2"}, {"itemName": "test3"}] }'  
```


This will keep retrying if an event is not sent, until the retries are exhausted and finally a CompensationActivity will be executed.

If you start a new instance and then send an event, while it is still retrying:

```sh
http ":8080/event-start?instanceId=<INSTANCE_ID>" content=test
```

You should see that the retry loop is stopped and the NextActivity is executed.

As you can see, after retrying four times, the event was received and the workflow moved to the next activity, without running any compensation activity.


### Perf Test


`siege -c1 -t1S --content-type "application/json" 'http://localhost:8080/start POST {"id": "123", "customer": "salaboy", "amount": 10, "paymentItems": [ {"itemName": "test"}, {"itemName": "test2"}, {"itemName": "test3"}] }'`

`siege -c50 -t10S --content-type "application/json" 'http://localhost:8080/start POST {"id": "123", "customer": "salaboy", "amount": 10, "paymentItems": [ {"itemName": "test"}, {"itemName": "test2"}, {"itemName": "test3"}] }'`


## Observability

The Zipkin container is started and configured for Dapr to send traces from the application.

Because Zipkin is started with Testcontainers, to find the exposed ports you will need to run:

```
docker ps
```

Find the Zipkin container and check for the mapped port on localhost:

```
CONTAINER ID  IMAGE                                  COMMAND               CREATED         STATUS                   PORTS                                                       NAMES
fd9e08addd6f  docker.io/testcontainers/ryuk:0.11.0   /bin/ryuk             34 seconds ago  Up 35 seconds            0.0.0.0:41353->8080/tcp                                     testcontainers-ryuk-2bfe7007-c07b-4a37-bfc9-4f6fdec11e84
490036349a1f  docker.io/confluentinc/cp-kafka:7.5.0  -c while [ ! -f /...  34 seconds ago  Up 35 seconds            0.0.0.0:34275->2181/tcp, 0.0.0.0:33459->9093/tcp, 9092/tcp  gifted_williamson
711c83bfd445  docker.io/openzipkin/zipkin:latest                           32 seconds ago  Up 32 seconds (healthy)  0.0.0.0:38517->9411/tcp, 9410/tcp                           reverent_brahmagupta
85078f4b20db  docker.io/daprio/placement:1.15.4      ./placement -port...  30 seconds ago  Up 30 seconds            0.0.0.0:45027->50005/tcp                                    cranky_gould
8cc6565f609d  docker.io/daprio/scheduler:1.15.4      ./scheduler --por...  30 seconds ago  Up 30 seconds            0.0.0.0:33689->51005/tcp                                    confident_margulis
eb61eafd25e5  docker.io/testcontainers/sshd:1.2.0    echo ${USERNAME}:...  29 seconds ago  Up 30 seconds            0.0.0.0:33429->22/tcp                                       nifty_lederberg
07e10c2ef589  docker.io/daprio/daprd:1.15.4          ./daprd --app-id ...  29 seconds ago  Up 29 seconds            0.0.0.0:36415->3500/tcp, 0.0.0.0:41827->50001/tcp           cool_diffie
f24ccad95619  quay.io/microcks/microcks-uber:1.11.2                        28 seconds ago  Up 28 seconds            0.0.0.0:44195->8080/tcp, 0.0.0.0:41255->9090/tcp            modest_shaw

```

For this example: `711c83bfd445  docker.io/openzipkin/zipkin:latest -> 0.0.0.0:38517->9411/tcp`, the `MAPPED_PORT` is `38517`

Then to access the Zipkin instance, point your browser to http://localhost:`<MAPPED_PORT>`/

If you want to change where Zipkin is hosted, you can run the application without the test configurations (`mvn spring-boot:run`) and
configure in the `application.properties` file the follow property with the new Zipkin URL:

```properties
zipkin.tracing.endpoint:https://zipkinhostedserver:port
```

## Actuator & Micrometer

This example shows how to record the time of execution of different parts of the workflow. This example uses micrometer to record the execution time of scheduleNewWorkflow, callActivity, the logic executed inside an Activity and the total workflow execution time. The Workflow definition call two activities, where the second one introduces a 100ms delay that is reflected in the metrics.

You can check the following endpoints to fetch the metrics for this example:

```
http :8080/actuator/metrics
```

```
http :8080/actuator/metrics/activity.first-activity
```



The Prometheus Actuator is also enabled, you can scrape prometheus in this endpoint: 

```properties
http :8080/actuator/prometheus
```

For configuring prometheus and metrics parameters you can check this documentation page: 
- [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html#actuator.metrics.export.prometheus)

