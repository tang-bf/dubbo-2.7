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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance;

import java.util.List;

/**
 * LoadBalance. (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Load_balancing_(computing)">Load-Balancing</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 */
@SPI(RandomLoadBalance.NAME)
public interface LoadBalance {
    /*dubbo 负载均衡算法
    hash算法  常见对服务器数量取摸
    优点是简单易用，大多数分库分表规则就采取的这种方式。一般是提前根据数据量，预先估算好分区数。
    其缺点是由于扩容或收缩节点导致节点数量变化时，节点的映射关系需要重新计算，会导致数据进行迁移。
    假设是一个缓存服务，数据的迁移会导致在迁移的时间段内，有缓存是失效的。缓存失效可能会导致缓存击穿，缓存穿透，缓存雪崩。
    所以扩容时通常采用翻倍扩容，避免数据映射全部被打乱，导致全量迁移的情况，这样只会发生50%的数据迁移。
    一致性hash算法：在hash算法的基础上，用于解决互联网中的Hotspot问题，将网络上的流量动态的划分到不通的服务器处理。
    是对2^32取摸
    求出不同服务器的哈希值，然后映射到一个范围为0-2^32-1的数值空间的圆环中，收尾衔接；
    当一个客户端请求过来时，对请求中的某些参数进行哈希计算后，也会得出一个哈希值，此值在哈希环上也会有对应的位置，
    这个请求会沿着顺时针的方向，寻找最近的服务器来处理它，如果找不到就用第一台处理；
    在一致性哈希算法中，不管是增加节点，还是宕机节点，受影响的区间仅仅是增加或者宕机服务器在哈希环空间中，
    逆时针方向遇到的第一台服务器之间的区间，其它区间不会受到影响。
    一致性hash算法也有缺点，当节点很少的时候可能会出现这样的分布情况，A服务会承担大部分请求。这种情况就叫做数据倾斜。
    为解决这问题，引入了虚拟节点机制，即对每一个服务节点计算多个hash，每个计算结果位置都放置一个此服务节点，成为虚拟节点，
    具体实现可以你在服务器ip或者主机名的后面增加编号实现。
    每台服务器计算三个虚拟节点，于是可以分别计算 “Node A#1”、“Node A#2”、“Node A#3”、“Node B#1”、“Node B#2”、“Node B#3”的哈希值
    ，于是形成六个虚拟节点： 定位到“Node A#1”、“Node A#2”、“Node A#3”三个虚拟节点的数据均定位到Node A上。这样就解决了服务节点少时数据倾斜的问题。
    在实际应用中，通常将虚拟节点数设置为32甚至更大，因此即使很少的服务节点也能做到相对均匀的数据分布。

    一致性哈希算法的时候，大多应该是在缓存场景下的使用，因为在一个优秀的哈希算法加持下，其上下线节点对整体数据的影响(迁移)都是比较友好的
    为什么Dubbo在负载均衡策略里面提供了基于一致性哈希的负载均衡策略？它的实际使用场景是什么？


     */


    /**
     * select one invoker in list.
     *
     * @param invokers   invokers.
     * @param url        refer url
     * @param invocation invocation.
     * @return selected invoker.
     */
    @Adaptive("loadbalance")
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException;

}