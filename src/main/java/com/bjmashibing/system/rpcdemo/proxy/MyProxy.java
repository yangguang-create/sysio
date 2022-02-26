package com.bjmashibing.system.rpcdemo.proxy;


import com.bjmashibing.system.rpcdemo.rpc.Dispatcher;
import com.bjmashibing.system.rpcdemo.rpc.protocol.MyContent;
import com.bjmashibing.system.rpcdemo.rpc.transport.ClientFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

/**
 * @author: 马士兵教育
 * @create: 2020-08-16 20:11
 */
public class MyProxy {

    public static <T>T proxyGet(Class<T>  interfaceInfo){
        //实现各个版本的动态代理。。。。

        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};

        //TODO  LOCAL REMOTE  实现：  用到dispatcher  直接给你返回，还是本地调用的时候也代理一下：因为这样可以加入AOP功能.

        Dispatcher dis =Dispatcher.getDis();
        return (T) Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                Object res=null;
                Object o = dis.get(interfaceInfo.getName());
                if(o== null){
                    //就要走rpc
                    String name = interfaceInfo.getName();
                    String methodName = method.getName();
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    //TODO  rpc  就像小火车拉货  content是service的具体数据，但是还需要header层完成IO传输的控制
                    MyContent content = new MyContent();
                    content.setArgs(args);
                    content.setName(name);
                    content.setMethodName(methodName);
                    content.setParameterTypes(parameterTypes);
                    //TODO 未来的小火车可能会变

                    /**
                     * 封装完数据包后，需要考虑的是将数据包分到哪个服务上面，
                     * 到某个服务的过程中，需要考虑是复用之前的连接还是重新创建连接.
                     * 1,缺失了注册发现，zk
                     * 2,第一层负载面向的provider
                     * 3，consumer  相当于一个线程池  面向 service；并发就有木桶效应，倾斜
                     * serviceA：这就是个provider，需要去将数据发送到哪个服务上，这就涉及到了负载均衡.
                     *      ipA:port：对某个服务来说，又分为发送数据包的时候，使用一个连接还是复用之前的连接，这和上层的协议
                     *                相关，如果是有状态的协议，那就可以复用一个连接。如果是无状态的协议，那就要每次请求重新创建连接.
                     *          socket1(是否复用连接，)
                     *          socket2
                     *          可以开启多个连接,目的是使用数据包把服务端的网卡填满。
                     *      ipB:port
                     */
                    CompletableFuture resF = ClientFactory.transport(content);

                    res =  resF.get();//阻塞的

                }else{
                    //就是local
                    //插入一些插件的机会，做一些扩展：之所以是本地的也使用反射的方式来调用，是以后可以在该方法执行前后做一些增强。类似于AOP.
                    System.out.println("lcoal FC....");
                    Class<?> clazz = o.getClass();
                    try {
                        Method m = clazz.getMethod(method.getName(), method.getParameterTypes());
                        res = m.invoke(o, args);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }

                }

                //不管是本地的调动，还是远程的调用.最后都是返回调用的结果.
                return  res;

                //TODO 应该在service的方法执行的时候确定是本地的还是远程的，用到dispatcher来区分下

            }
        });


    }


}
