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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LeastActiveLoadBalance
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 * 筛选出active调用最少的  只有一个则直接调用；多个权重不一样，加权随机；多个权重一样，直接随机一个
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {
/*
    负载均衡最小活跃数算法  配合ActiveLimitFilter active使用
    活跃调用数越小说明该服务提供者效率越高，单位时间内处理的请求更多。此时应将请求分配给该服务提供者。
    具体实现：L每个服务提供者对应一个活跃数active.初始时都为o,每收到一个请求，active加1，完成后减1.在服务运行一段时间后，性能好的处理请求速度
    更快，活跃数下降的快。这样的服务提供者能优先获取到新的请求。除了最小活跃数，还引入权重值。加权最小活跃数试下。
    有最小活跃数用最小活跃数，没有最小活跃数根据权重选择，权重一样则随机返回的负载均衡算法。
 */
    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // The least active value of all invokers
        // 最小的活跃数
        int leastActive = -1;
        // The number of invokers having the same least active value (leastActive)
        // 具有相同“最小活跃数”的服务者提供者（以下用 Invoker 代称）数量
        int leastCount = 0;
        // The index of invokers having the same least active value (leastActive)
        // leastIndexs 用于记录具有相同“最小活跃数”的 Invoker 在 invokers 列表中的下标信息
        int[] leastIndexes = new int[length];
        // the weight of every invokers
        int[] weights = new int[length];
        // The sum of the warmup weights of all the least active invokers
        int totalWeight = 0;
        // The weight of the first least active invoker
        //第一个最小活跃数的 Invoker 权重值，用于与其他具有相同最小活跃数的 Invoker 的权重进行对比，
        // 以检测是否“所有具有相同最小活跃数的 Invoker 的权重”均相等
        int firstWeight = 0;
        // Every least active invoker has the same weight value?
        boolean sameWeight = true;


        // Filter out all the least active invokers
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // Get the active number of the invoker
            // 获取 Invoker 对应的活跃数
            //active 在rpcstatus中AtomicInteger   在rpcstatus中begincount  endcount方法中分别increment和decrement
            //activelimitfilter(CONSUMER 用于消费端 ) executelimitfilter（PROVIDER ） 使用
            //最少活跃数负载均衡算法必须配合ActiveLimitFilter使用，位于RpcStatus类的active字段才会起作用，否则，它就是一个基于权重的算法。
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // Get the weight of the invoker's configuration. The default value is 100.
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            weights[i] = afterWarmup;
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            // 发现更小的活跃数，重新开始
            if (leastActive == -1 || active < leastActive) {
                // Reset the active number of the current invoker to the least active number
                // 使用当前活跃数 active 更新最小活跃数 leastActive
                leastActive = active;
                // Reset the number of least active invokers
                leastCount = 1;// 更新 leastCount 为 1
                // Put the first least active invoker first in leastIndexes
                leastIndexes[0] = i;// 记录当前下标值到 leastIndexs 中
                // Reset totalWeight
                totalWeight = afterWarmup;
                // Record the weight the first least active invoker
                firstWeight = afterWarmup;
                // Each invoke has the same weight (only one invoker here)
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
            } else if (active == leastActive) {// 当前 Invoker 的活跃数 active 与最小活跃数 leastActive 相同
                // Record the index of the least active invoker in leastIndexes order
                leastIndexes[leastCount++] = i;// 在 leastIndexs 中记录下当前 Invoker 在 invokers 集合中的下标
                // Accumulate the total weight of the least active invoker
                totalWeight += afterWarmup; // 累加权重
                // If every invoker has the same weight?
                if (sameWeight && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // Choose an invoker from all the least active invokers
        if (leastCount == 1) { // 当只有一个 Invoker 具有最小活跃数，此时直接返回该 Invoker 即可
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        // 有多个 Invoker 具有相同的最小活跃数，但它们之间的权重不同
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.// 随机生成一个 [0, totalWeight) 之间的数字
            /*
            ThreadLoaclRandom  1.7之后juc包下面的
            解决了Random类在多线程下多个线程竞争内部唯一的原子性种子变量而导致大量线程自旋重试的不足
            new Random 创建一个默认随机数生成器，使用默认的种子；
                                 public int nextInt(int bound) {
                            if (bound <= 0) 参数检查
                                throw new IllegalArgumentException(BadBound);

                            int r = next(31); 老的种子生成新的种子
                            新的种子计算随机数
                            int m = bound - 1;
                            if ((bound & m) == 0)  // i.e., bound is a power of 2
                                r = (int)((bound * (long)r) >> 31);
                            else {
                                for (int u = r;
                                     u - (r = u % bound) + m < 0;
                                     u = next(31))
                                    ;
                            }
                            return r;
                        }
                        next(31)对应调用的方法
                     protected int next(int bits) {
                        long oldseed, nextseed;
                        AtomicLong seed = this.seed;
                        do {
                          6  oldseed = seed.get(); 获取当前原子变量种子的值
                          7  nextseed = (oldseed * multiplier + addend) & mask; 据当前种子值计算新的种子
                        } while (!seed.compareAndSet(oldseed, nextseed));   CAS加while循环
                        多线程下都执行到6代码，那么拿的当前种子是同一个，然后执行7，拿到的新种子是也是相同的，
                        通过cas的时候只会有一个线程更新成功，失败的线程自旋重试  降低了并发性能。
                        return (int)(nextseed >>> (48 - bits));
                    }

                    每个线程维护自己的一个种子变量，每个线程生成随机数时候根据自己老的种子计算新的种子，
                    并使用新种子更新老的种子，然后根据新种子计算随机数，就不会存在竞争问题，这会大大提高并发性能
                    和threadlocal思想差不多
                    threadLocalRandom中并没有具体存放种子，具体的种子是存放到具体的调用线程的threadLocalRandomSeed变量里面的，
                     并且加了@sun.misc.Contended("tlr") 防止缓存伪共享
             */
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                // 循环让随机数减去具有最小活跃数的 Invoker 的权重值，
                // 当 offset 小于等于0时，返回相应的 Invoker
                // 获取权重值，并让随机数减去权重值
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        // 如果权重相同或权重为0时，随机返回一个 Invoker
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
