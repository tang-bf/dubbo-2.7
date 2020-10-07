package com.luban.dubbo_spi.wrapper;

import com.luban.dubbo_spi.HttpServer;
import org.apache.dubbo.common.URL;

/**
 * *************书山有路勤为径***************
 * 鲁班学院
 * 往期资料加木兰老师  QQ: 2746251334
 * VIP课程加安其拉老师 QQ: 3164703201
 * 讲师：周瑜老师
 * *************学海无涯苦作舟***************
 */
public class TomcatServerWrapper implements HttpServer {
    private HttpServer httpServer;

    public TomcatServerWrapper(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void start(URL url) {
        System.out.println("wrapper");
        httpServer.start(url);
    }
}
