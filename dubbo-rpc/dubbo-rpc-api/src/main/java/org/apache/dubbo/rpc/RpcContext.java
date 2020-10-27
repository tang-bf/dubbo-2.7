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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.Experimental;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.InternalThreadLocal;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.rpc.Constants.ASYNC_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_KEY;


/**
 * Thread local context. (API, ThreadLocal, ThreadSafe)
 * <p>
 * Note: RpcContext is a temporary state holder. States in RpcContext changes every time when request is sent or received.
 * For example: A invokes B, then B invokes C. On service B, RpcContext saves invocation info from A to B before B
 * starts invoking C, and saves invocation info from B to C after B invokes C.
 *
 * @export
 * @see org.apache.dubbo.rpc.filter.ContextFilter
 */
public class RpcContext {

    /**
     *
     * use internal thread local to improve performance
     * 作为一个 Dubbo 应用，它既可能是发起请求的消费者，也可能是接收请求的提供者。
     * 每一次发起或者收到 RPC 调用的时候，上下文信息都会发生变化。
     * 比如说：A 调用 B，B 调用 C。这个时候 B 既是消费者也是提供者。
     * 那么当 A 调用 B，B 还是没调用 C 之前，RpcContext 里面保存的是 A 调用 B 的上下文信息。
     * 当 B 开始调用 C 了，说明 A 到 B 之前的调用已经完成了，那么之前的上下文信息就应该清除掉。
     * 这时 RpcContext 里面保存的应该是 B 调用 C 的上下文信息。否则会出现上下文污染的情况。
     * 而这个上下文信息，就是维护在当前线程的 InternalThreadLocal 里面的。这个对象是在 ContextFilter 这个拦截器维护的。
     */
    // FIXME REQUEST_CONTEXT  老版本用的是threadlocal
    // InternalThreadLocal 来源基于netty  可看到https://github.com/apache/dubbo/pull/1745·提交中有netty之父  还有apache公司的一个对话
    //借助于netty中的fastthreadlocal
    private static final InternalThreadLocal<RpcContext> LOCAL = new InternalThreadLocal<RpcContext>() {
        @Override
        protected RpcContext initialValue() {
            return new RpcContext();
            /**
             * 有一个和业务没有一毛钱关系的参数，比如 traceId ，纯粹是为了做日志追踪用。
             * 一个和业务无关的参数一路透传干啥玩意？
             * 通常我们的做法是放在 ThreadLocal 里面，作为一个全局参数，在当前线程中的任何一个地方都可以直接读取。
             * 当然，如果你有修改需求也是可以的，视需求而定。
             * 绝大部分的情况下，ThreadLocal 是适用于读多写少的场景中。
             * (1) spring源码中  分析@bean 的时候，会把当前方法和调用方法比较是否一样，调用方方就是存到threadlocal上面
             * (2)spring事物基于aop实现，@Transactional 注解如果想要生效，那么其调用方，需要是被 Spring 动态代理后的类。
             * 同一个类里面，使用 this 调用被 @Transactional 注解修饰的方法时，是不会生效的。
             * 因为this不是代理对象，可有三种解决方案，
             * 1.expose-proxy="true" 通过  AopContext.currentProxy() 调用
             * 2.java配置类上添加注解@EnableAspectJAutoProxy(exposeProxy = true)方式暴漏代理对象，
             * 然后在service中通过代理对象AopContext.currentProxy()去调用方法
             * 3.ervice中自动装配service自身，然后同过装配对象调用
             * aopcontext 里面维护了一个threadlocal
             * (3)mybatis 的分页插件，PageHelper。
             * pagehelper.startpage(1,10);
             * 紧跟着的第一个的select方法会被分页
             * list<user> list=usermapper.selectif(1);
             * pagehelper方法使用了静态的threadlocal参数，分页参数和线程是绑定的
             * 只要你可以保证在 PageHelper 方法调用后紧跟 MyBatis 查询方法，这就是安全的。
             * 因为 PageHelper 在 finally 代码段中自动清除了 ThreadLocal 存储的对象。
             * 所以就算代码在进入 Executor 前发生异常，导致线程不可用的情况，比如常见的接口方法名称和 XML 中的不匹配，
             * 导致找不到 MappedStatement ，由于 finally 的自动清除，也不会导致 ThreadLocal 参数被错误的使用。
             *
             */
            //jdk原生ThreadLocal
        //  每个线程thread都持有一个 ThreadLocal.ThreadLocalMap  threadlocals变量(还有一个inheritableThreadLocals 继承threadlocal)
            //ThreadLocalMap 有一个entry 基于WeakReference 弱引用
            /*threadlocal set方法时，threadlocal本身作为key  put进去
             默认为空，只有第一次set或者get时候才会创建，不使用本地变量的时候需要调用remove方法
             THreadLocalMap中的Entry的key使用的是ThreadLocal对象的弱引用，
             在没有其他地方对ThreadLoca依赖，ThreadLocalMap中的ThreadLocal对象就会被回收掉，
             但是对应的不会被回收，这个时候Map中就可能存在key为null但是value不为null的项，
             这需要实际的时候使用完毕及时调用remove方法避免内存泄漏。
             同一个ThreadLocal变量在父线程中被设置值后，在子线程中是获取不到的。
             （threadLocals中为当前调用线程对应的本地变量，所以二者自然是不能共享的）
             InheritableThreadLocal类则可以做到这个功能
             */
        }
    };

    // FIXME RESPONSE_CONTEXT
    private static final InternalThreadLocal<RpcContext> SERVER_LOCAL = new InternalThreadLocal<RpcContext>() {
        @Override
        protected RpcContext initialValue() {
            return new RpcContext();
        }
    };

    protected final Map<String, Object> attachments = new HashMap<>();
    private final Map<String, Object> values = new HashMap<String, Object>();

    private List<URL> urls;

    private URL url;

    private String methodName;

    private Class<?>[] parameterTypes;

    private Object[] arguments;

    private InetSocketAddress localAddress;

    private InetSocketAddress remoteAddress;

    private String remoteApplicationName;

    @Deprecated
    private List<Invoker<?>> invokers;
    @Deprecated
    private Invoker<?> invoker;
    @Deprecated
    private Invocation invocation;

    // now we don't use the 'values' map to hold these objects
    // we want these objects to be as generic as possible
    private Object request;
    private Object response;
    private AsyncContext asyncContext;

    private boolean remove = true;


    protected RpcContext() {
    }

    /**
     * get server side context.
     *
     * @return server context
     */
    public static RpcContext getServerContext() {
        return SERVER_LOCAL.get();
    }

    public static void restoreServerContext(RpcContext oldServerContext) {
        SERVER_LOCAL.set(oldServerContext);
    }

    /**
     * remove server side context.
     *
     * @see org.apache.dubbo.rpc.filter.ContextFilter
     */
    public static void removeServerContext() {
        SERVER_LOCAL.remove();
    }

    /**
     * get context.
     *
     * @return context
     */
    public static RpcContext getContext() {
        return LOCAL.get();
    }

    public boolean canRemove() {
        return remove;
    }

    public void clearAfterEachInvoke(boolean remove) {
        this.remove = remove;
    }

    public static void restoreContext(RpcContext oldContext) {
        LOCAL.set(oldContext);
    }

    /**
     * remove context.
     *
     * @see org.apache.dubbo.rpc.filter.ContextFilter
     */
    public static void removeContext() {
        removeContext(false);
    }

    /**
     * customized for internal use.
     *
     * @param checkCanRemove if need check before remove
     */
    public static void removeContext(boolean checkCanRemove) {
        if (LOCAL.get().canRemove()) {
            LOCAL.remove();
        }
    }

    /**
     * Get the request object of the underlying RPC protocol, e.g. HttpServletRequest
     *
     * @return null if the underlying protocol doesn't provide support for getting request
     */
    public Object getRequest() {
        return request;
    }

    public void setRequest(Object request) {
        this.request = request;
    }

    /**
     * Get the request object of the underlying RPC protocol, e.g. HttpServletRequest
     *
     * @return null if the underlying protocol doesn't provide support for getting request or the request is not of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequest(Class<T> clazz) {
        return (request != null && clazz.isAssignableFrom(request.getClass())) ? (T) request : null;
    }

    /**
     * Get the response object of the underlying RPC protocol, e.g. HttpServletResponse
     *
     * @return null if the underlying protocol doesn't provide support for getting response
     */
    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    /**
     * Get the response object of the underlying RPC protocol, e.g. HttpServletResponse
     *
     * @return null if the underlying protocol doesn't provide support for getting response or the response is not of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getResponse(Class<T> clazz) {
        return (response != null && clazz.isAssignableFrom(response.getClass())) ? (T) response : null;
    }

    /**
     * is provider side.
     *
     * @return provider side.
     */
    public boolean isProviderSide() {
        return !isConsumerSide();
    }

    /**
     * is consumer side.
     *
     * @return consumer side.
     */
    public boolean isConsumerSide() {
        return getUrl().getParameter(SIDE_KEY, PROVIDER_SIDE).equals(CONSUMER_SIDE);
    }

    /**
     * get CompletableFuture.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getCompletableFuture() {
        return FutureContext.getContext().getCompletableFuture();
    }

    /**
     * get future.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> Future<T> getFuture() {
        return FutureContext.getContext().getCompletableFuture();
    }

    /**
     * set future.
     *
     * @param future
     */
    public void setFuture(CompletableFuture<?> future) {
        FutureContext.getContext().setFuture(future);
    }

    public List<URL> getUrls() {
        return urls == null && url != null ? (List<URL>) Arrays.asList(url) : urls;
    }

    public void setUrls(List<URL> urls) {
        this.urls = urls;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    /**
     * get method name.
     *
     * @return method name.
     */
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * get parameter types.
     *
     * @serial
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    /**
     * get arguments.
     *
     * @return arguments.
     */
    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    /**
     * set local address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setLocalAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.localAddress = InetSocketAddress.createUnresolved(host, port);
        return this;
    }

    /**
     * get local address.
     *
     * @return local address
     */
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * set local address.
     *
     * @param address
     * @return context
     */
    public RpcContext setLocalAddress(InetSocketAddress address) {
        this.localAddress = address;
        return this;
    }

    public String getLocalAddressString() {
        return getLocalHost() + ":" + getLocalPort();
    }

    /**
     * get local host name.
     *
     * @return local host name
     */
    public String getLocalHostName() {
        String host = localAddress == null ? null : localAddress.getHostName();
        if (StringUtils.isEmpty(host)) {
            return getLocalHost();
        }
        return host;
    }

    /**
     * set remote address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setRemoteAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.remoteAddress = InetSocketAddress.createUnresolved(host, port);
        return this;
    }

    /**
     * get remote address.
     *
     * @return remote address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * set remote address.
     *
     * @param address
     * @return context
     */
    public RpcContext setRemoteAddress(InetSocketAddress address) {
        this.remoteAddress = address;
        return this;
    }

    public String getRemoteApplicationName() {
        return remoteApplicationName;
    }

    public RpcContext setRemoteApplicationName(String remoteApplicationName) {
        this.remoteApplicationName = remoteApplicationName;
        return this;
    }

    /**
     * get remote address string.
     *
     * @return remote address string.
     */
    public String getRemoteAddressString() {
        return getRemoteHost() + ":" + getRemotePort();
    }

    /**
     * get remote host name.
     *
     * @return remote host name
     */
    public String getRemoteHostName() {
        return remoteAddress == null ? null : remoteAddress.getHostName();
    }

    /**
     * get local host.
     *
     * @return local host
     */
    public String getLocalHost() {
        String host = localAddress == null ? null :
                localAddress.getAddress() == null ? localAddress.getHostName()
                        : NetUtils.filterLocalHost(localAddress.getAddress().getHostAddress());
        if (host == null || host.length() == 0) {
            return NetUtils.getLocalHost();
        }
        return host;
    }

    /**
     * get local port.
     *
     * @return port
     */
    public int getLocalPort() {
        return localAddress == null ? 0 : localAddress.getPort();
    }

    /**
     * get remote host.
     *
     * @return remote host
     */
    public String getRemoteHost() {
        return remoteAddress == null ? null :
                remoteAddress.getAddress() == null ? remoteAddress.getHostName()
                        : NetUtils.filterLocalHost(remoteAddress.getAddress().getHostAddress());
    }

    /**
     * get remote port.
     *
     * @return remote port
     */
    public int getRemotePort() {
        return remoteAddress == null ? 0 : remoteAddress.getPort();
    }

    /**
     * also see {@link #getObjectAttachment(String)}.
     *
     * @param key
     * @return attachment
     */
    public String getAttachment(String key) {
        Object value = attachments.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null; // or JSON.toString(value);
    }

    /**
     * get attachment.
     *
     * @param key
     * @return attachment
     */
    @Experimental("Experiment api for supporting Object transmission")
    public Object getObjectAttachment(String key) {
        return attachments.get(key);
    }

    /**
     * set attachment.
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext setAttachment(String key, String value) {
        return setObjectAttachment(key, (Object) value);
    }

    public RpcContext setAttachment(String key, Object value) {
        return setObjectAttachment(key, value);
    }

    @Experimental("Experiment api for supporting Object transmission")
    public RpcContext setObjectAttachment(String key, Object value) {
        if (value == null) {
            attachments.remove(key);
        } else {
            attachments.put(key, value);
        }
        return this;
    }

    /**
     * remove attachment.
     *
     * @param key
     * @return context
     */
    public RpcContext removeAttachment(String key) {
        attachments.remove(key);
        return this;
    }

    /**
     * get attachments.
     *
     * @return attachments
     */
    @Deprecated
    public Map<String, String> getAttachments() {
        return new AttachmentsAdapter.ObjectToStringMap(this.getObjectAttachments());
    }

    /**
     * get attachments.
     *
     * @return attachments
     */
    @Experimental("Experiment api for supporting Object transmission")
    public Map<String, Object> getObjectAttachments() {
        return attachments;
    }

    /**
     * set attachments
     *
     * @param attachment
     * @return context
     */
    public RpcContext setAttachments(Map<String, String> attachment) {
        this.attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment);
        }
        return this;
    }

    /**
     * set attachments
     *
     * @param attachment
     * @return context
     */
    @Experimental("Experiment api for supporting Object transmission")
    public RpcContext setObjectAttachments(Map<String, Object> attachment) {
        this.attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment);
        }
        return this;
    }

    public void clearAttachments() {
        this.attachments.clear();
    }

    /**
     * get values.
     *
     * @return values
     */
    public Map<String, Object> get() {
        return values;
    }

    /**
     * set value.
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext set(String key, Object value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    /**
     * remove value.
     *
     * @param key
     * @return value
     */
    public RpcContext remove(String key) {
        values.remove(key);
        return this;
    }

    /**
     * get value.
     *
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * @deprecated Replace to isProviderSide()
     */
    @Deprecated
    public boolean isServerSide() {
        return isProviderSide();
    }

    /**
     * @deprecated Replace to isConsumerSide()
     */
    @Deprecated
    public boolean isClientSide() {
        return isConsumerSide();
    }

    /**
     * @deprecated Replace to getUrls()
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Invoker<?>> getInvokers() {
        return invokers == null && invoker != null ? (List) Arrays.asList(invoker) : invokers;
    }

    public RpcContext setInvokers(List<Invoker<?>> invokers) {
        this.invokers = invokers;
        if (CollectionUtils.isNotEmpty(invokers)) {
            List<URL> urls = new ArrayList<URL>(invokers.size());
            for (Invoker<?> invoker : invokers) {
                urls.add(invoker.getUrl());
            }
            setUrls(urls);
        }
        return this;
    }

    /**
     * @deprecated Replace to getUrl()
     */
    @Deprecated
    public Invoker<?> getInvoker() {
        return invoker;
    }

    public RpcContext setInvoker(Invoker<?> invoker) {
        this.invoker = invoker;
        if (invoker != null) {
            setUrl(invoker.getUrl());
        }
        return this;
    }

    /**
     * @deprecated Replace to getMethodName(), getParameterTypes(), getArguments()
     */
    @Deprecated
    public Invocation getInvocation() {
        return invocation;
    }

    public RpcContext setInvocation(Invocation invocation) {
        this.invocation = invocation;
        if (invocation != null) {
            setMethodName(invocation.getMethodName());
            setParameterTypes(invocation.getParameterTypes());
            setArguments(invocation.getArguments());
        }
        return this;
    }

    /**
     * Async invocation. Timeout will be handled even if <code>Future.get()</code> is not called.
     *
     * @param callable
     * @return get the return result from <code>future.get()</code>
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> asyncCall(Callable<T> callable) {
        try {
            try {
                setAttachment(ASYNC_KEY, Boolean.TRUE.toString());
                final T o = callable.call();
                //local invoke will return directly
                if (o != null) {
                    if (o instanceof CompletableFuture) {
                        return (CompletableFuture<T>) o;
                    }
                    return CompletableFuture.completedFuture(o);
                } else {
                    // The service has a normal sync method signature, should get future from RpcContext.
                }
            } catch (Exception e) {
                throw new RpcException(e);
            } finally {
                removeAttachment(ASYNC_KEY);
            }
        } catch (final RpcException e) {
            CompletableFuture<T> exceptionFuture = new CompletableFuture<>();
            exceptionFuture.completeExceptionally(e);
            return exceptionFuture;
        }
        return ((CompletableFuture<T>) getContext().getFuture());
    }

    /**
     * one way async call, send request only, and result is not required
     *
     * @param runnable
     */
    public void asyncCall(Runnable runnable) {
        try {
            setAttachment(RETURN_KEY, Boolean.FALSE.toString());
            runnable.run();
        } catch (Throwable e) {
            // FIXME should put exception in future?
            throw new RpcException("oneway call error ." + e.getMessage(), e);
        } finally {
            removeAttachment(RETURN_KEY);
        }
    }

    /**
     * @return
     * @throws IllegalStateException
     */
    @SuppressWarnings("unchecked")
    public static AsyncContext startAsync() throws IllegalStateException {
        RpcContext currentContext = getContext();
        if (currentContext.asyncContext == null) {
            currentContext.asyncContext = new AsyncContextImpl();
        }
        currentContext.asyncContext.start();
        return currentContext.asyncContext;
    }

    protected void setAsyncContext(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
    }

    public boolean isAsyncStarted() {
        if (this.asyncContext == null) {
            return false;
        }
        return asyncContext.isAsyncStarted();
    }

    public boolean stopAsync() {
        return asyncContext.stop();
    }

    public AsyncContext getAsyncContext() {
        return asyncContext;
    }

}