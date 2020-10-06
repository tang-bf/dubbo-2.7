package com.tbh.provider.service;

import com.tbh.DemoService;
import com.tbh.DemoServiceListener;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.Argument;
import org.apache.dubbo.config.annotation.Method;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


//@Service
public class DefaultDemoService implements DemoService {

    private final Map<String, DemoServiceListener> listeners = new ConcurrentHashMap<String, DemoServiceListener>();

    @Override
    public String sayHello(String name)  {
        System.out.println("执行了");
        URL url = RpcContext.getContext().getUrl();
        //打印出访问的协议及端口  可看到20883 20881 20882 随机访问的
        //D:\zookeeperGUI\ZooInspector\build\zookeeper-dev-ZooInspector.jar
        // 通过ooInspector ui客户端看到zk节点上 dubbo/com.tbh.Demoservice 下providers consumers等信息
        //也可通过dubbo admin控制台查看
        return String.format("%s：%s, Hello, %s", url.getProtocol(), url.getPort(), name);  // 正常访问
//        throw new RpcException();
    }

}
