/*
 * Copyright 2025 The Dapr Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
limitations under the License.
*/

package io.dapr.springboot.extreme.workflows;

import com.redis.testcontainers.RedisContainer;
import io.dapr.testcontainers.*;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TestConfiguration()
public class DaprTestContainersConfig {

  @Bean
  public Network getDaprNetwork(Environment env) {
    boolean reuse = env.getProperty("reuse", Boolean.class, false);
    if (reuse) {
      Network defaultDaprNetwork = new Network() {
        @Override
        public String getId() {
          return "dapr-network";
        }

        @Override
        public void close() {

        }

        @Override
        public Statement apply(Statement base, Description description) {
          return null;
        }
      };

      List<com.github.dockerjava.api.model.Network> networks = DockerClientFactory.instance().client().listNetworksCmd()
          .withNameFilter("dapr-network").exec();
      if (networks.isEmpty()) {
        Network.builder().createNetworkCmdModifier(cmd -> cmd.withName("dapr-network")).build().getId();
        return defaultDaprNetwork;
      } else {
        return defaultDaprNetwork;
      }
    } else {
      return Network.newNetwork();
    }
  }

  @Bean
  @ConditionalOnProperty(prefix = "tests", name = "dapr.local", havingValue = "true")
  GenericContainer zipkinContainer(Network network) {
    GenericContainer zipkinContainer = new GenericContainer(DockerImageName.parse("openzipkin/zipkin:latest"))
            .withNetwork(network)
            .withEnv("JAVA_OPTS","-Xms1g -Xmx1g -XX:+ExitOnOutOfMemoryError")
            .withExposedPorts(9411)
            .withNetworkAliases("zipkin");

    return zipkinContainer;
  }



  @Bean
  public RedisContainer redisContainer(Network daprNetwork, Environment env){
    boolean reuse = env.getProperty("reuse", Boolean.class, false);
    return new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME)
            .withNetwork(daprNetwork)
            .withReuse(reuse)
            .withNetworkAliases("redis");
  }


  @Bean
  @ServiceConnection
  @ConditionalOnProperty(prefix = "tests", name = "dapr.local", havingValue = "true")
  public DaprContainer daprContainer(Network daprNetwork,
                                     GenericContainer zipkinContainer) {

    Map<String, String> kafkaProperties = new HashMap<>();
    kafkaProperties.put("brokers", "kafka:19092");
    kafkaProperties.put("authType", "none");

    Map<String, String> redisProps = new HashMap<>();
    redisProps.put("redisHost", "redis:6379");
    redisProps.put("redisPassword", "");
    redisProps.put("actorStateStore", "true");


//    * If you want to use custom images you can do so like this:
//    DockerImageName myDaprImage = DockerImageName.parse("salaboy/daprd:1.15.4")
//            .asCompatibleSubstituteFor("daprio/daprd:1.15.4");
//    DockerImageName myDaprPlacementImage = DockerImageName.parse("salaboy/placement:1.15.4")
//            .asCompatibleSubstituteFor("daprio/placement:1.15.4");
//    DockerImageName myDaprSchedulerImage = DockerImageName.parse("salaboy/scheduler:1.15.4")
//            .asCompatibleSubstituteFor("daprio/scheduler:1.15.4");


    DockerImageName myDaprImage = DockerImageName.parse("daprio/daprd:1.15.7");
    return new DaprContainer(myDaprImage)
            .withAppName("workflows")
            .withNetwork(daprNetwork)
// You need to set the Placement and Scheduler image if you want to use custom registries
//            .withPlacementImage(myDaprPlacementImage)
//            .withSchedulerImage(myDaprSchedulerImage)
            .withComponent(new Component("kvstore", "state.redis", "v1",
                    redisProps))
            .withConfiguration(new Configuration("daprConfig",
                    new TracingConfigurationSettings("1", true, null,
                            new ZipkinTracingConfigurationSettings("http://zipkin:9411/api/v2/spans")), null))
            .withSubscription(new Subscription("app", "pubsub", "pubsubTopic", "/asyncpubsub/continue"))
//  Uncomment if you want to troubleshoot Dapr related problems
            .withDaprLogLevel(DaprLogLevel.DEBUG)
            .withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()))
            .withAppPort(8080)
            .withAppHealthCheckPath("/actuator/health")
            .withAppChannelAddress("host.testcontainers.internal")
            .dependsOn(zipkinContainer);
  }


}
