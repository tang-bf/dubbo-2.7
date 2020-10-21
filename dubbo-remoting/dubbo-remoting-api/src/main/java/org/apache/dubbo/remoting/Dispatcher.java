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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllDispatcher;

/**
 * ChannelHandlerWrapper (SPI, Singleton, ThreadSafe)
 */
@SPI(AllDispatcher.NAME)
/*dubbo消费端线程池模型 io线程
线程池策略:线程池客户端的默认实现是 cached，服务端的默认实现是 fixed。
客户端，除了用户线程外，还会有一个线程名称为DubboClientHandler-ip:port的线程池，其默认实现是cache线程池。
服务端，除了有boss线程、worker线程（io线程），还有一个线程名称为DubboServerHandler-ip:port的线程池，其默认实现是fixed线程池。
通过spi配置文件对外提供
resources目录下的com.alibaba.dubbo.common.threadpool.ThreadPool的文件
fixed=com.alibaba.dubbo.common.threadpool.support.fixed.FixedThreadPool
jdk中 Executors.newFixedThreadPool()
cached=com.alibaba.dubbo.common.threadpool.support.cached.CachedThreadPool
jdk中Executors.newCachedThreadPool()
limited=com.alibaba.dubbo.common.threadpool.support.limited.LimitedThreadPool
jdk中Executors.newCachedThreadPool()，区别是limited永远不会过期，keepalive时间最大值
 EagerThreadPool  * When the core threads are all in busy,
 * create new thread instead of putting task into blocking queue.

 自定义ThreadPoolExecutor，直接继承JDK的ThreadPoolExecutor，重写部分逻辑
 execute方法添加一次重试,也有一个atomiclong submittedTaskCount
实现TaskQueue，直接继承LinkedBlockingQueue ，重写 offer 方法
// return false to let executor create new worker.
        if (currentPoolThreadSize < executor.getMaximumPoolSize()) {
            return false;
        }
 和tomcat的思路很相同

 相比传统的jdk线程池
我们也可以先把最大线程数用完，然后再让任务进入队列。
通过自定义队列，重写其 offer 方法就可以实现。目前 Tomcat 和 Dubbo 都提供了这样策略的线程池。
tomcat分析见下文；
因为 Tomcat 处理的多是 IO 密集型任务，用户在前面等着响应呢，结果你明明还能处理，却让用户的请求入队等待？
jdk自带线程池，核心参数最大参数，拒绝策略，也可以控制核心参数超时时间。allowsCoreThreadTimeOut ,岗创建的时候线程池中是没有线程的，
只有当有任务提交的时候才会去创建，new的时候可以控制预先启动预热核心线程，prestartCoreThread,prestartAllCoreThreads ;
拒绝策略，AbortPolicy，CallerRunsPolicy，DiscardPolicy，DiscardOldestPolicy；
jdk自带，提交任务，核心已满（否的话就创建线程执行任务）是》》》队列满不满（不满的话加入队列），满的话看线程池满不满，不满的话就创建线程执行任务
，满的话就执行拒绝策略。看着更适合于CPU密集型任务；
 Tomcat/Jetty 需要处理大量客户端请求任务，如果采用原生线程池，(Jetty 采用自研方案，内部实现 QueuedThreadPool 线程池组件)
 一旦接受请求数量大于线程池核心线程数，这些请求就会被放入到队列中，等待核心线程处理。
 这样做显然降低这些请求总体处理速度，所以两者都没采用 JDK 原生线程池。
tomcat: 自定义ThreadPoolExecutor，直接继承JDK的ThreadPoolExecutor，重写部分逻辑
 实现TaskQueue，直接继承LinkedBlockingQueue ，重写 offer 方法。
 核心方法execute()，Tomcat简单做了修改，还是将工作任务交给父类，也就是Java原生线程池处理，
 但增加了一个重试策略。如果原生线程池执行拒绝策略的情况，抛出 RejectedExecutionException 异常。
 这里将会捕获，然后重新再次尝试将任务加入到 TaskQueue ，尽最大可能执行任务。
 public void execute(Runnable command, long timeout, TimeUnit unit) {
        this.submittedCount.incrementAndGet();

        try {
            super.execute(command);
        } catch (RejectedExecutionException var9) {
            if (!(super.getQueue() instanceof TaskQueue)) {
                this.submittedCount.decrementAndGet();
                throw var9;
            }

            TaskQueue queue = (TaskQueue)super.getQueue();

            try {
                //拒绝后，再尝试入队，还不行则抛出异常
                if (!queue.force(command, timeout, unit)) {
                    this.submittedCount.decrementAndGet();
                    throw new RejectedExecutionException(sm.getString("threadPoolExecutor.queueFull"));
                }
            } catch (InterruptedException var8) {
                this.submittedCount.decrementAndGet();
                throw new RejectedExecutionException(var8);
            }
        }
    }
submittedCount 变量。这是 Tomcat 线程池内部一个重要的参数，它是一个 AtomicInteger 变量，将会实时统计已经提交到线程池中，
但还没有执行结束的任务。也就是说 submittedCount 等于线程池队列中的任务数加上线程池工作线程正在执行的任务。
public class TaskQueue extends LinkedBlockingQueue<Runnable> {
	......
        //tomcat-util-10.0.0-M6.jar
    public boolean offer(Runnable o) {
        if (this.parent == null) {
            //1.若没有给出tomcat线程池对象，则调用父类方法
            return super.offer(o);
        } else if (this.parent.getPoolSize() == this.parent.getMaximumPoolSize()) {
            //2.若当前线程数已达到最大线程数，则放入阻塞队列
            return super.offer(o);
        } else if (this.parent.getSubmittedCount() <= this.parent.getPoolSize()) {
            //3.若当前已提交任务数量小于等于最大线程数，说明此时有空闲线程。此时将任务放入队列中，立刻会有空闲线程来处理该任务
            return super.offer(o);
        } else {
            //4.若当前线程数小于最大线程数，发返回false，此时线程池将会创建新线程！！！
            return this.parent.getPoolSize() < this.parent.getMaximumPoolSize() ? false : super.offer(o);
        }
    }
    jdk原生中//如果阻塞队列返回false，将会走else分支，去创建新的线程
    if (isRunning(c) && workQueue.offer(command)) {
    else if (!addWorker(command, false))
            reject(command);
tomcat通过扩展的方式改变了线程池运行机制。


spring中的threadpooltaskexecutor，常用方式就是做为BEAN注入到容器中；
线程池中大小小于核心，就创建并处理请求；当等于核心时，就放入workqueue，尺子中的空闲线程去队列取任务处理；
当队列放不下的时候，新创建线程加入线程池，并处理请求。当达到最大的时候，就执行拒绝策略。当线程池中线程数大于核心时，多余的线程会等待alivetime，没有请求就销毁；

dubbo消费端线程池模型
 1.业务线程发出请求，拿到一个 Future 实例。
 2、在调用 future.get() 之前，先调用 ThreadlessExecutor.wait()，wait
 会使业务线程在一个阻塞队列上等待，直到队列中被加入元素。
 3、当业务数据返回后，生成一个 Runnable Task 并放ThreadlessExecutor 队列。
  4、业务线程将 Task 取出并在本线程中执行反序列化业务数据并 set 到 Future。
   5、业务线程拿到结果直接返回。
相比于老的线程池模型，新的线程模型由业务线程自己负责监测并解析返回结果，免去了额外的消费端线程池开销。


业务线程发出请求，拿到一个 Future 实例。
2、业务线程紧接着调用 future.get 阻塞等待业务结果返回。
 3、当业务数据返回后，交由独立的 Consumer 端线程池进行反序列化等处理，并调用 future.set 将反序列化后的业务结果置回。
  4、业务线程拿到结果直接返回。

NamedThreadFactory NamedinternalThreadFactory(后面这个是为了使用internalthread,用internalthreadlocal，在threadlocal分析过)线程池中线程创建的工
官网上有段话，2.7.5之前，消费端大量服务并发大流量时，经常出现消费端线程数分配过多的问题，客户端默认都是cached，个线程池并没有限制线程数量，所以会出现消费端线程数分配多的问题。
分配过多。多和过多还不一样，2.7.5 版本之前，每一个链接对应一个客户端线程池。相当于做了链接级别的线程隔离，
但实际上这个线程隔离是没有必要的。反而影响了性能。2.7.5 版本里面，不管多少链接，共用一个客户端线程池，
 threadless executor就是从多个线程池改为了共用一个线程池。

dubbo默认用netty底层通讯（mina 及grizzly），见源码  DubboProtocol openserver createserver  基于netty4
服务提供方nettyserver使用两级线程池 eventloopgrup（boss）线程用于客户端的请求，并把请求分发给worker线程处理，boss和worker称为io线程
如果服务方的逻辑能够迅速完成，并且不会发起新的io请求，那么直接在io线程处理更快，减少了超线程池的调度
如果处理逻辑很慢，或者需要发起新的IO请求，比如需要查询数据库，则IO线程必须派发请求到新的线程池进行处理，否则IO线程会阻塞，将导致不能接收其它请求。
线程模型：
alldispatcher 所有消息都被派发到业务线程池 请求，响应，连接，端开。心跳等
AllChannelHandler中connected，disconnected，received，caught统一通过业务线程池处理。
client>>>>>eventloopgroup(boss)>>>eventloopgroup(worker)>>>>>threadpool
directdispatcher 全部io线程处理
DirectChannelHandler 的处理没有用到线程池，统一由IO线程去处理。
client>>>>>eventloopgroup(boss)>>>eventloopgroup(worker)
messageonlydispatcher 请求响应消息派发到业务线程池，其他在io，连接断开心跳等
executiondispatcher 只把请求类消息抓发到业务线程池，响应连接断开心跳在io
connectionordereddispatcher io上连接断开放入队列，依次执行，其他发到线程池；
业务数据返回后， 反序列化的操作到底是在独立的 Consumer 端线程池里面进行的还是在 IO 线程里面进行的？
在老的（2.7.5 版本之前）线程池模型中，当业务数据返回后，
默认在 IO 线程上进行反序列化操作，如果配置了 decode.in.io 参数为 false（默认为 true），
则延迟到独立的客户端线程池进行反序列化操作。
dubbo的协议分为消息头和消息体，头主要存一些原信息，魔数，数据包类型，调用方式，事件表示，序列化编码号，状态，请求编号，消息体长度
消息体中：dubbo version,service name,service version,method name,parameter types,args,attachments(上下文信息)
客户端发起的请求严格按照上面顺序写入，服务端按同样顺序解析；
0-7 魔数高位 0xda00 8-15 低位 0xbb ，dubbo中用来干什么 》》》  它是用来解决网络粘包/解包问题的 ？
tcp粘包半包问题咋解决的，netty Netty提供了各种Decoder，LineBasedFrameDecoder`,`LengthFieldBasedFrameDecoder
dubbo协议解析入口， NettyCodecAdapter 用内部类InternalDecoder decode，每一个channel对应一个私有的decoder
dubbo在处理tcp的粘包和版时是借助InternalDecoder的buffer缓存对象来缓存不完整的dubbo协议栈数据，
等待下次inbound事件，合并进去。主要是uffer变量来解决的。
 (java中二进制文件中 魔数 0xCAFEBABY，
每个 class 文件的头 4 个字节就是魔数，它的唯一作用就是确定这个文件是否为一个能被 JVM 接受的 class 文件。)
16：数据包类型 0  response 1 request
17:调用方式 0 单向 1 双向
18:事件标识 0 请求或响应  1 心跳
19序列化编号：hession2 java compatcedjava nativejava kryo fst(zk序列化用的Hadoop的jute，此外还有protobuf thirft )
24-31 状态  ok clienttimeout servertimeout badrequest badresponse ....
32-95 8字节请求编号，就是前面分析的defaultyre中
96-127 消息体长度 运行时计算*
Dubbo 默认的报文长度限制  default_pay_load 8*1024*1024  配置为-1  不限制

 */
public interface Dispatcher {

    /**
     * dispatch the message to threadpool.
     *
     * @param handler
     * @param url
     * @return channel handler
     */
    @Adaptive({Constants.DISPATCHER_KEY, "dispather", "channel.handler"})
    // The last two parameters are reserved for compatibility with the old configuration
    ChannelHandler dispatch(ChannelHandler handler, URL url);

}
