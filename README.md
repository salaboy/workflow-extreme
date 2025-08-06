# workflow-extreme

This repository includes a set of workflow patterns and examples that shows how to use Dapr Workflow with Spring Boot applications.

The `workflows` application can be started by running the following commands for local development:

```
cd workflows/
mvn spring-boot:test-run
```

**Note:** If you want to run this examples against Diagrid's Catalyst, you need to comment out the following line in the `application.properties` file located in `src/test/resources/`:

```
#comment out to run tests against Catalyst
tests.dapr.local=true
```

You can set your Catalyst ENV Variables or set the following Spring Boot Application properties:

In your `application.properties` file:
```
dapr.client.httpEndpoint=<Your Catalyst Project HTTP Endpoint>
dapr.client.grpcEndpoint=<Your Catalyst Project GRPC Endpoint>
dapr.client.apiToken=<Your Catalyst APP ID API Token>
```


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

![Zipkin](imgs/zipkin.png) 


### Multi Retries with Compensation and Timeout exceptions

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
http :8080/multiretry/start id="123" customer="salaboy" amount=10
```

You should see the application output:
```
io.dapr.workflows.WorkflowContext        : Workflow instance 29ac53db-26f0-43cd-9cfe-b248eaa99ef5 started
io.dapr.workflows.WorkflowContext        : Let's call the first activity for payment 123.
i.d.s.w.multiretry.FirstActivity         : Executing First Activity.
io.dapr.workflows.WorkflowContext        : First Activity for payment: 123 completed.
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 0.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 1
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 1.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 2
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 2.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 3
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 3.
....

```

This will keep retrying if an event is not sent, until the retries are exhausted and finally a CompensationActivity will be executed.

If no event is sent you should see:

```
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Retries exhausted after 10 retries. 
io.dapr.workflows.WorkflowContext        : Let's execute the Compensation Activity. 
i.d.s.w.multiretry.CompensationActivity  : Executing Compensation Activity.
io.dapr.workflows.WorkflowContext        : Compensation Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Workflow 123 Completed. 
```

If you start a new instance and then send an event, while it is still retrying:

```sh
http :8080/multiretry/event content=test
```

You should see that the retry loop is stopped and the NextActivity is executed:

```
io.dapr.workflows.WorkflowContext        : Workflow instance 0cd39871-9a4c-4b92-9541-a2638967c4cb started
io.dapr.workflows.WorkflowContext        : Let's call the first activity for payment 123.
i.d.s.w.multiretry.FirstActivity         : Executing First Activity.
io.dapr.workflows.WorkflowContext        : First Activity for payment: 123 completed.
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 0.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 1
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 1.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 2
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 2.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 3
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 3.
io.dapr.workflows.WorkflowContext        : Wait for event timed out. 
io.dapr.workflows.WorkflowContext        : Let's execute the Retry Activity. Retry: 4
i.d.s.w.multiretry.RetryActivity         : Executing Retry Activity.
io.dapr.workflows.WorkflowContext        : Retry Activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Wait for event, for 2 seconds, iteration: 4.
i.d.s.w.m.MultiRetryRestController       : Event received with content {"content": "test"}.
io.dapr.workflows.WorkflowContext        : Event arrived with content: {"content": "test"}
io.dapr.workflows.WorkflowContext        : We got the event after 4 retries, let's execute the Next Activity. 
i.d.s.workflows.multiretry.NextActivity  : Executing Next Activity.
io.dapr.workflows.WorkflowContext        : Next activity executed successfully. 
io.dapr.workflows.WorkflowContext        : Workflow 123 Completed.

```

As you can see, after retrying four times, the event was received and the workflow moved to the next activity, without running any compensation activity.



### Perf Test


`siege -c1 -t1S --content-type "application/json" 'http://localhost:8080/start POST {"id": "123", "customer": "salaboy", "amount": 10, "paymentItems": [ {"itemName": "test"}, {"itemName": "test2"}, {"itemName": "test3"}] }'`

`siege -c50 -t10S --content-type "application/json" 'http://localhost:8080/start POST {"id": "123", "customer": "salaboy", "amount": 10, "paymentItems": [ {"itemName": "test"}, {"itemName": "test2"}, {"itemName": "test3"}] }'`
