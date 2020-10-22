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
import org.apache.dubbo.rpc.cluster.router.tag.TagRouter;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;

/**
 * ConsistentHashLoadBalance
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes";

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        //TagRouter filterInvoker invokers.stream().filter(predicate).collect(Collectors.toList());
        //老版本获取的是原始的hash值  int identityHashCode = System.identityHashCode(invokers);
        //为啥改了，因为TagRouter filterInvoker invokers.stream().filter(predicate).collect(Collectors.toList());
        /*会修改invokers的值，导致这里受影响，即使没有服务上下线操作的时候，一致性hash负载均衡算法都需要进行hash环的映射
         hashCode方法 ：一个对象不覆盖这个方法，那它会继承Object类的实现，是一个native的方法。这个时候，它会根据对象的内存地址返回哈希值。
         如果一个对象覆盖了hashCode方法，我们仍然想获得它的内存地址计算的Hash值，应该怎么办呢？
         native int identityHashCode(Object x);
          集合中的元素没有发生变化，导致 hashCode 没有变化。所以改正用了hashcode方法
            因为 List 重写了 hashCode()方法，其算出的 hashCode 只和 list 中的元素相关：
            由于装元素的容器(集合)已经不是原来的容器了，所以 identityHashCode 发生了变化
         一个类被加载的时候，hashCode是被存放在对象头里面的Mark Word里面的。在32位的JVM中，它会占25位；在64位的JVM中，它会占31位。
         每个Java对象都有对象头。如果是非数组类型，则用2个字宽来存储对象头，如果是数组，则会用3个字宽来存储对象头。
         在32位虚拟机中，一个字宽是32位；在64位虚拟机中，一个字宽是64位。
         当一个对象已经计算过identity hash code，它就无法进入偏向锁状态；
         当一个对象当前正处于偏向锁状态，并且需要计算其identity hash code的话，则它的偏向锁会被撤销，并且锁会膨胀为重量级锁；
          那什么时候对象会计算identity hash code呢？
          是当你调用未覆盖的Object.hashCode()方法或者System.identityHashCode(Object o)时候了。
         */
        int invokersHashCode = invokers.hashCode();
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        // 如果 invokers 是一个新的 List 对象，意味着服务提供者数量发生了变化，可能新增也可能减少了。
        // 此时 selector.identityHashCode != identityHashCode 条件成立
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            // 创建新的 ConsistentHashSelector
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
            //hash环构建完成  自己家的代码
            for (Map.Entry<Long, Invoker<T>> longInvokerEntry : selector.virtualInvokers.entrySet()) {
                System.out.println("key 哈希值是："+longInvokerEntry.getKey());
                System.out.println("虚拟节点  value ="+longInvokerEntry.getValue());
            }
        }
        // 调用 ConsistentHashSelector 的 select 方法选择 Invoker
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {

        private final TreeMap<Long, Invoker<T>> virtualInvokers;

        private final int replicaNumber;

        private final int identityHashCode;

        private final int[] argumentIndex;

        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            // 获取虚拟节点数，默认为160
            //parameter key="hash.nodes" value="自定义" 表示服务端映射的虚拟节点数
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
            // 获取参与 hash 计算的参数下标值，默认对第一个参数进行 hash 运算
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) {
                    // 对 address + i 进行 md5 运算，得到一个长度为16的字节数组
                    byte[] digest = md5(address + i);
                    // 对 digest 部分字节进行4次 hash 运算，得到四个不同的 long 型正整数
                    for (int h = 0; h < 4; h++) {
                        // h = 0 时，取 digest 中下标为 0 ~ 3 的4个字节进行位运算
                        // h = 1 时，取 digest 中下标为 4 ~ 7 的4个字节进行位运算
                        // h = 2, h = 3 时过程同上
                        long m = hash(digest, h);
                        // 将 hash 到 invoker 的映射关系存储到 virtualInvokers 中，
                        // virtualInvokers 需要提供高效的查询操作，因此选用 TreeMap 作为存储结构
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public Invoker<T> select(Invocation invocation) {
            //将参数转为 key
            String key = toKey(invocation.getArguments());
            byte[] digest = md5(key);//d对key进行MD5运算
            // 取 digest 数组的前四个字节进行 hash 运算，再将 hash 值传给 selectForKey 方法，
            // 寻找合适的 Invoker
            return selectForKey(hash(digest, 0));
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            //virtualInvokers treemap 存储虚拟节点
            // 到 TreeMap 中查找第一个节点值大于或等于当前 hash 的 Invoker
            // 沿着顺时针的方向找遇到的第一个节点  如果节点为空就使用第一个节点
            //虚拟节点都存储在TreeMap中。顺时针查询的逻辑由TreeMap保证
            /*treemap 存储kv，通过红黑树实现（r-b tree）
                继承了navigablemap接口，navigablemap继承了sortedmap,可支持一系列的导航定位及导航操作方法，需要treemap自己实现
                实现了Cloneable接口，可被克隆，实现了Serializable接口，可序列化
                是通过红黑树实现，红黑树结构天然支持排序，默认情况下通过Key值的自然顺序进行排序
                reeMap自动排序的，默认情况下comparator为null，这个时候按照key的自然顺序进行排
                序，然而并不是所有情况下都可以直接使用key的自然顺序，有时候我们想让Map的自动排序按照我们自己的规则，
                这个时候你就需要传递Comparator的实现类；
                TreeMap的存储结构既然是红黑树，那么必然会有唯一的根节点Entry<K,V> root
                Map中key-val对的数量，也即是红黑树中节点Entry的数量 nt size = 0;
                int modCount = 0;  红黑树结构的调整次数
                treemap put方法流程
                put--->>红黑树是否建立，root是否为null ---》否--》》》new entry创建红黑树赋值给root
                          遍历红黑树各个节点，与传入的key值比较，找到相同的key就更新key所在的entry的value
                          找不到相同的key就遍历到最后节点作为待插入节点的父节点，然后插入子节点，调整红黑树的结构，使其满足规则>>>.结束

             */
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            //如果 hash 大于 Invoker 在圆环上最大的位置，此时 entry = null，
            // 需要将 TreeMap 的头节点赋值给 entry
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
