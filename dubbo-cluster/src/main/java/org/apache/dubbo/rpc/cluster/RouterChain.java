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
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Router chain
 */
public class RouterChain<T> {

    // full list of addresses from registry, classified by method name.
    private List<Invoker<T>> invokers = Collections.emptyList();

    // containing all routers, reconstruct every time 'route://' urls change.
    private volatile List<Router> routers = Collections.emptyList();

    // Fixed router instances: ConfigConditionRouter, TagRouter, e.g., the rule for each instance may change but the
    // instance will never delete or recreate.
    private List<Router> builtinRouters = Collections.emptyList();

    public static <T> RouterChain<T> buildChain(URL url) {
        return new RouterChain<>(url);
    }

    private RouterChain(URL url) {
        /**
         * tagrouterfactory
         * app   dubbo/config/dubbo/dubbo-demo-consumer-application.condition-router
         * service dubbo/config/dubbo/org.apache.demoservice:1.1.1:g1.condition-router
         * 根据routerfactory根据url生成各个类型的router
         * 这里产生的已经可用了  对象内部已经有真实数据了  消费者启动，构造路由连的时候绑定监听器 手动先获取哦数据
         * 单对于标签路由没有  暂时还相当于没有数据的路由  还没有从配置中心获取数据
         * 引入服务，获得服务提供者的url时候 ，才会去监听tag-router节点的内容
         * /dubbo/config/dubbo/dubbo-demo-provider-application.tag-router
         * 1.request.tag=red 时优先选择 tag=red 的 provider。若集群中不存在与请求标记对应的服务，可以降级请求 tag=null 的 provider，即默认 provider。
         * 2.request.tag=null 时，只会匹配 tag=null 的 provider。即使集群中存在可用的服务，
         * 若 tag 不匹配就无法调用，这与规则1不同，携带标签的请求可以降级访问到无标签的服务，
         * 但不携带标签/携带其他种类标签的请求永远无法访问到其他标签的服务。
         */
        List<RouterFactory> extensionFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class)
                .getActivateExtension(url, "router");

        List<Router> routers = extensionFactories.stream()
                .map(factory -> factory.getRouter(url))
                .collect(Collectors.toList());

        initWithRouters(routers);
    }

    /**
     * the resident routers must being initialized before address notification.
     * FIXME: this method should not be public
     */
    public void initWithRouters(List<Router> builtinRouters) {
        this.builtinRouters = builtinRouters;
        this.routers = new ArrayList<>(builtinRouters);
        this.sort();
    }

    /**
     * If we use route:// protocol in version before 2.7.0, each URL will generate a Router instance, so we should
     * keep the routers up to date, that is, each time router URLs changes, we should update the routers list, only
     * keep the builtinRouters which are available all the time and the latest notified routers which are generated
     * from URLs.
     *
     * @param routers routers from 'router://' rules in 2.6.x or before.
     */
    public void addRouters(List<Router> routers) {
        List<Router> newRouters = new ArrayList<>();
        newRouters.addAll(builtinRouters);
        newRouters.addAll(routers);
        CollectionUtils.sort(newRouters);
        this.routers = newRouters;
    }

    private void sort() {
        Collections.sort(routers);
    }

    /**
     *
     * @param url
     * @param invocation
     * @return
     */
    public List<Invoker<T>> route(URL url, Invocation invocation) {
        List<Invoker<T>> finalInvokers = invokers;
        for (Router router : routers) {
            finalInvokers = router.route(finalInvokers, url, invocation);
        }
        return finalInvokers;
    }

    /**
     * Notify router chain of the initial addresses from registry at the first time.
     * Notify whenever addresses in registry change.
     */
    public void setInvokers(List<Invoker<T>> invokers) {
        this.invokers = (invokers == null ? Collections.emptyList() : invokers);
        routers.forEach(router -> router.notify(this.invokers));
    }
}
