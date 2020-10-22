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

    /*hash算法引起的缓存失效情景分析缓存失效
       缓存击穿是指一个请求要访问的数据，缓存中没有，但数据库中有。
       一般来说就是缓存过期了。但是这时由于并发访问这个缓存的用户特别多，
       这是一个热点key，这么多用户的请求同时过来，在缓存里面都没有取到数据，
       所以又同时去访问数据库取数据，引起数据库流量激增，压力瞬间增大，直接崩溃给你看。
       1:互斥锁方案的思路就是如果从redis中没有获取到数据，就让一个线程去数据库查询数据，
       然后构建缓存，其他的线程就等着，过一段时间后再从redis中去获取。  能解决问题，但是一个线程构建缓存的时候，另外的线程都在睡眠或者轮询。
       2:后台续命方案的思想就是，后台开一个定时任务，专门主动更新即将过期的数据
       设置xxx这个热点key的时候，同时设置了过期时间为60分钟，那后台程序在第55分钟的时候，
       会去数据库查询数据并重新放到缓存中，同时再次设置缓存为60分钟。
       3:永不过期 简单粗暴
       缓存穿透是指一个请求要访问的数据，缓存和数据库中都没有，而用户短时间、高密度的发起这样的请求，
       每次都打到数据库服务上，给数据库造成了压力。一般来说这样的请求属于恶意请求；
       1.缓存空对象就是在数据库即使查到的是空对象，我们也把这个空对象缓存起来。
       果在某个时间，缓存为空的记录，在数据库里面有值了，你怎么办？
       解决方法一:设置缓存的时候，同时设置一个过期时间，这样过期之后，就会重新去数据库查询最新的数据并缓存起来。
        解决方法二:如果对实时性要求非常高的话，那就写数据库的时候，同时写缓存。这样可以保障实时性。
        解决方法三:如果对实时性要求不是那么高，那就写数据库的时候给消息队列发一条数据，让消息队列再通知处理缓存的逻辑去数据库取出最新的数据。
        对于恶意攻击，请求的时候key往往各不相同，且只请求一次，那你要把这些key都缓存起来的话，
        因为每个key都只请求一次，那还是每次都会请求数据库，没有保护到数据库呀？
        本质上布隆过滤器是一种数据结构，比较巧妙的概率型数据结构（probabilistic data structure），
        特点是高效地插入和查询，可以用来告诉你 “某样东西一定不存在或者可能存在”。
        相比于传统的 List、Set、Map 等数据结构，它更高效、占用空间更少，但是缺点是其返回的结果是概率性的，而不是确切的。
        布隆过滤器说某个值存在时，这个值可能不存在;当它说不存在时，那就肯定不存在。
        所以布隆过滤器返回的结果是概率性的，所以它能缓解数据库的压力，并不能完全挡住，这点必须要明确。
        guava组件可以开箱即用的实现一个布隆过滤器，但是guava是基于内存的，所以不太适用于分布式环境下。
        要在分布式环境下使用布隆过滤器，那还得redis出马，redis可以用来实现布隆过滤器。
        缓存雪崩是指缓存中大多数的数据在同一时间到达过期时间，而查询数据量巨大，这时候，又是缓存中没有，
        数据库中有的情况了。请求都打到数据库上，引起数据库流量激增，压力瞬间增大，直接崩溃给你看；
        存击穿不同的是，缓存击穿指大量的请求并发查询同一条数据。
        缓存雪崩是不同数据都到了过期时间，导致这些数据在缓存中都查询不到，或是缓存服务直接挂掉了，所以缓存都没有了。
        1. 加互斥锁
        2.错峰"过期
        3.引入redis集群，使用主从加哨兵。用Redis Cluster部署集群很方便的
        4.如果Cluster集群也挂了怎么办呢  ？（ 限流器+本地缓存）一下引入限流器，比如 Hystrix，然后实现服务降级。

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