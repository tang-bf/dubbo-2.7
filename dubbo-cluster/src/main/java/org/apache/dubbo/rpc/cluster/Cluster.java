/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;

/**
 * Cluster. (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Computer_cluster">Cluster</a>
 * <a href="http://en.wikipedia.org/wiki/Fault-tolerant_system">Fault-Tolerant</a>
 *
 */
@SPI(Cluster.DEFAULT)
public interface Cluster {
    String DEFAULT = FailoverCluster.NAME;
    //Cluster集群的作用是什么？
    /*Dubbo的粘滞连接？
    dubbo定义了cluster  clusterinvoker 集群cluster的用途是讲多个服务器合并为一个clusterinvoker，并将这个invoker暴露给服务消费者
    这样做的好处就是，对于消费者来说，只需要通过这个clusterinvoker进行远程盗用即可，至于是哪个服务提供者及失败后如何处理，都交给集群模块处理
    就群模块作废提供者消费者的中间层，为消费者屏蔽了提供者的情况，这样消费者专心处理远程调用相关事宜。
    mock=org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper 本地伪装，通常用于服务降级，比如某验权服务，当服务提供方全部挂掉后，客户端不抛出异常，而是通过 Mock 数据返回授权失败。
failover=org.apache.dubbo.rpc.cluster.support.FailoverCluster 失败自动切换重试其他服务器，m默认retris=2 失败重试两次，所以会总共调用三次
failfast=org.apache.dubbo.rpc.cluster.support.FailfastCluster 快速失败，只发起一次调用，失败立即报错，非幂等性操作，新增记录
failsafe=org.apache.dubbo.rpc.cluster.support.FailsafeCluster 失败安全，出现异常时，直接忽略。通常用于写入审计日志等操作
failback=org.apache.dubbo.rpc.cluster.support.FailbackCluster  失败自动恢复，后台记录失败请求，定时重发。通常用于消息通知操作。
forking=org.apache.dubbo.rpc.cluster.support.ForkingCluster  并行调用多个服务器，只要一个成功即返回。通常用于实时性要求较高的读操作，但需要浪费更多服务资源。可通过 forks="2" 来设置最大并行数。
available=org.apache.dubbo.rpc.cluster.support.AvailableCluster 获取可用的服务方。遍历所有Invokers通过invoker.isAvalible判断服务端是否活着，只要一个有为true，直接调用返回，不管成不成功。
mergeable=org.apache.dubbo.rpc.cluster.support.MergeableCluster 分组聚合，将集群中的调用结果聚合起来，然后再返回结果。比如菜单服务，接口一样，但有多种实现，用group区分，现在消费方需从每种group中调用一次返回结果，合并结果返回，这样就可以实现聚合菜单项。
broadcast=org.apache.dubbo.rpc.cluster.support.BroadcastCluster 广播调用所有提供者，逐个调用，任意一台报错则报错。通常用于通知所有提供者更新缓存或日志等本地资源信息。
zone-aware=org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareCluster  Dubbo 2.7.5以后，对于多注册中心订阅的场景，选址时的多了一层注册中心集群间的负载均衡。
这个注册中心集群间的负载均衡的实现就是：zone-aware Cluster。
1.preferred="true"注册中心的地址将被优先选择，只有该中心无可用地址时才Fallback到其他注册中心  <dubbo:registry address="zookeeper://${zookeeper.address1}" preferred="true" />
2.同 zone 优先：
选址时会和流量中的zone key做匹配，流量会优先派发到相同zone的地址
<dubbo:registry address="zookeeper://${zookeeper.address1}" zone="beijing" />
3.权重轮询：
来自北京和上海集群的地址，将以10:1的比例来分配流量
<dubbo:registry id="beijing" address="zookeeper://${zookeeper.address1}" weight="100" />
<dubbo:registry id="shanghai" address="zookeeper://${zookeeper.address2}" weight="10" />
4.默认方法：
选择第一个可用的即可。
failover、failfast、failsafe、failback、forking、broadcast这6种才属于集群容错的范畴
     */
    /**
     * Merge the directory invokers to a virtual invoker.
     *
     * @param <T>
     * @param directory
     * @return cluster invoker
     * @throws RpcException
     */
    @Adaptive
    <T> Invoker<T> join(Directory<T> directory) throws RpcException;

    static Cluster getCluster(String name) {
        return getCluster(name, true);
    }

    static Cluster getCluster(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            name = Cluster.DEFAULT;
        }
        return ExtensionLoader.getExtensionLoader(Cluster.class).getExtension(name, wrap);
    }
}