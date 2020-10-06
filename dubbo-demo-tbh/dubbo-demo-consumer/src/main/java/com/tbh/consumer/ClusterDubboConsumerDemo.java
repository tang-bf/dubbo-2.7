package com.tbh.consumer;

import com.tbh.DemoService;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@EnableAutoConfiguration
public class ClusterDubboConsumerDemo {

// Failover  失败自动切换 ，重试其他服务器，通过retries来设置重试次数（不含第一次调用）
    //Failfast  快速失败 直发器一次，失败立即报错，通常用于非幂等性的操作，比如新增操作
    // Failsafe 失败安全 出现异常时，忽略，用于写入审计日志操作
    // Failback 失败自动恢复，后台记录失败请求，定时重发。通常用于消息通知操作。 可看到服务端重试了
    // Forking 并行调用多个服务器，只要一个成功即返回。通常用于实时性要求较高的读操作，
// 但需要浪费更多服务资源。可通过 forks="2" 来设置最大并行数。
    // Broadcast 广播调用所有提供者，逐个调用，任意一台报错则报错
// 通常用于通知所有提供者更新缓存或日志等本地资源信息。
    //客户端异常抛出信息只出现打印了一次
    //mock 本地伪装 服务降级  stub本地存根                                 mock="force ....."   mock = "fail: return 123"
    @Reference(timeout = 1000, cluster = "failfast" ,mock = "true")
    //mock =true  调用失败会查找执行xxserviceMock逻辑 也可以配置某个类
    private DemoService demoService;

    public static void main(String[] args) throws IOException {
        ConfigurableApplicationContext context = SpringApplication.run(ClusterDubboConsumerDemo.class);

        DemoService demoService = context.getBean(DemoService.class);

        System.out.println((demoService.sayHello("周瑜")));

    }

}
