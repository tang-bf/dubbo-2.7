package com.tbh.provider.service;

import com.tbh.DemoService;
import com.tbh.DemoServiceListener;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.rpc.RpcContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

//为什么不用spring的service注解，dubbo自己要实现一个  spring的不能配置参数 ，dubbo的service注解已更改为dubboservice
//spring启动的时候也是需要将dubbo标记的service注解生成为bean，还需要服务的注册,启动Tomcat，jetty等等
/*怎么触发的呢
解析配置  @enabledubboconfig 用来将注解中的properties 转化成对应的xxxConfig对象
 @DubboComponentScan
spring生成了那个bean后，是一个普通bean
再生成一个servicebean 这个servicebean父类ServiceConfigBase 有个属性 ref ==> 普通bean
servicebean 这个bean实现了ServiceBean 这个bean实现了以前的版本是实现了applicationlistener
新版版中用了ApplicationEventPublisherAware 代替
ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean,
        ApplicationContextAware, BeanNameAware, ApplicationEventPublisherAware {
spring启动完了发布一个事件 进行服务的导出注册
 */
@Service(timeout = 3000)
public class TimeoutDemoService implements DemoService {

    private final Map<String, DemoServiceListener> listeners = new ConcurrentHashMap<String, DemoServiceListener>();

    @Override
    public String sayHello(String name) {
        System.out.println("开始执行"+name);

        // 服务执行5秒
        // 服务超时时间为3秒，但是执行了5秒，服务端会把任务执行完的
        //默认重试2次 默认使用的是failover集群容错方式
        //看到日志
        //开始执行周瑜
        //开始执行周瑜
        //执行结束
        //开始执行周瑜
        //执行结束
        // 服务的超时时间，是指如果服务执行时间超过了指定的超时时间则会抛一个warn
        //客户端会有异常抛出org.apache.dubbo.remoting.TimeoutException:
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("执行结束"+name);

        URL url = RpcContext.getContext().getUrl();
        return String.format("%s：%s, Hello, %s", url.getProtocol(), url.getPort(), name);  // 正常访问
    }

}
