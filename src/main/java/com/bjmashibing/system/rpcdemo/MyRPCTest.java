package com.bjmashibing.system.rpcdemo;

import com.bjmashibing.system.rpcdemo.proxy.MyProxy;
import com.bjmashibing.system.rpcdemo.rpc.Dispatcher;
import com.bjmashibing.system.rpcdemo.rpc.protocol.MyContent;
import com.bjmashibing.system.rpcdemo.rpc.protocol.Myheader;
import com.bjmashibing.system.rpcdemo.rpc.transport.MyHttpRpcHandler;
import com.bjmashibing.system.rpcdemo.rpc.transport.ServerDecode;
import com.bjmashibing.system.rpcdemo.rpc.transport.ServerRequestHandler;
import com.bjmashibing.system.rpcdemo.service.*;
import com.bjmashibing.system.rpcdemo.util.SerDerUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: 马士兵教育
 * @create: 2020-07-12 20:08
 * <p>
 * 12号的课开始手写RPC ，把前边的IO的课程都看看
 * http://mashibing.com/vip.html#%E5%91%A8%E8%80%81%E5%B8%88%E5%86%85%E5%AD%98%E4%B8%8Eio%E7%A3%81%E7%9B%98io%E7%BD%91%E7%BB%9Cio
 */

/*
    1，先假设一个需求，写一个RPC
    2，来回通信，连接数量，拆包？
    3，动态代理呀，序列化，协议封装
    4，连接池
    5，就像调用本地方法一样去调用远程的方法，面向java中就是所谓的 面向interface开发
 */


/**
 * 上节课，基本写了一个能发送
 * 小问题，当并发通过一个连接发送后，服务端解析bytebuf 转 对象的过程出错
 */
public class MyRPCTest {

    //多多包涵，如果一会翻车，请不要打脸。。。。。


    /*
    *
    *   使用netty的方式在服务端进行数据处理
    * */
    @Test
    public void startServer() {

        MyCar car = new MyCar();
        MyFly fly = new MyFly();

        Dispatcher dis = Dispatcher.getDis();

        dis.register(Car.class.getName(), car);
        dis.register(Fly.class.getName(), fly);

        NioEventLoopGroup boss = new NioEventLoopGroup(20);
        NioEventLoopGroup worker = boss;

        ServerBootstrap sbs = new ServerBootstrap();
        ChannelFuture bind = sbs.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        System.out.println("server accept cliet port: " + ch.remoteAddress().getPort());
                        ChannelPipeline p = ch.pipeline();

//                        //1，自定义的rpc：需要自己对请求的数据包进行编解码然后处理请求的的handler.
//                        p.addLast(new ServerDecode());
//                        p.addLast(new ServerRequestHandler(dis));
                        //在自己定义协议的时候你关注过哪些问题：粘包拆包的问题，header+body

                        //2，小火车，传输协议用的就是http了  <- 你可以自己学，自己解码网络上传输的byte[]
                        //其实netty提供了一套编解码：HttpObjectAggregator
                        p.addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1024*512))
                                .addLast(new ChannelInboundHandlerAdapter(){
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        //现在使用的是http 协议 ,  这个msg是一个啥：完整的http-request：应该是一个完整的http请求.
                                        //经过内在的编解码处理器处理之后，给到的就是封装好的HttpRequest对象.
                                        FullHttpRequest request = (FullHttpRequest) msg;

                                        System.out.println(request.toString());  //因为现在consumer使用的是一个现成的URL


                                        //这个就是consumer 序列化的MyContent
                                        ByteBuf content = request.content();
                                        byte[]  data = new byte[content.readableBytes()];
                                        content.readBytes(data);
                                        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(data));
                                        MyContent myContent = (MyContent)oin.readObject();

                                        String serviceName = myContent.getName();
                                        String method = myContent.getMethodName();
                                        Object c = dis.get(serviceName);
                                        Class<?> clazz = c.getClass();
                                        Object res = null;
                                        try {
                                            Method m = clazz.getMethod(method, myContent.getParameterTypes());
                                            res = m.invoke(c, myContent.getArgs());
                                        } catch (NoSuchMethodException e) {
                                            e.printStackTrace();
                                        } catch (IllegalAccessException e) {
                                            e.printStackTrace();
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }
                                        MyContent resContent = new MyContent();
                                        resContent.setRes(res);
                                        byte[] contentByte = SerDerUtil.ser(resContent);

                                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0,
                                                HttpResponseStatus.OK,
                                                Unpooled.copiedBuffer(contentByte));

                                        //服务端响应给客户端的时候也要设置响应体的长度，否则客户端拿到响应，但是解析不了.
                                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH,contentByte.length);

                                        //http协议，header+body
                                        ctx.writeAndFlush(response);
                                    }
                                });
                    }
                }).bind(new InetSocketAddress("localhost", 9090));
        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


    /*
    *   使用jetty在服务端进行数据处理
    * */
    @Test
    public void startHttpServer(){
        /*
        *   客户端不管是基于netty还是url方式发送数据，
        *   总归都是使用的是http协议的来传输数据.
        *   服务端收到都是http协议的报文.
        *   服务端只要使用基于http协议的工具来解析包即可.
        *   可以选择的产品有tomcat,jetty等.
        *
        * */
        MyCar car = new MyCar();
        MyFly fly = new MyFly();

        Dispatcher dis = Dispatcher.getDis();

        dis.register(Car.class.getName(), car);
        dis.register(Fly.class.getName(), fly);


        //tomcat jetty ---> 内部使用的是【servlet】
        Server server = new Server(new InetSocketAddress("localhost", 9090));
        ServletContextHandler handler = new ServletContextHandler(server, "/");
        server.setHandler(handler);
        handler.addServlet(MyHttpRpcHandler.class,"/*");  //web.xml

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //模拟comsumer端 && provider
    @Test
    public void get() {
//        new Thread(()->{
//            startServer();
//        }).start();
//
//        System.out.println("server started......");

        AtomicInteger num = new AtomicInteger(0);
        int size = 50;
        Thread[] threads = new Thread[size];
        for (int i = 0; i < size; i++) {
            threads[i] = new Thread(() -> {
                Car car = MyProxy.proxyGet(Car.class);//动态代理实现   //是真的要去触发 RPC调用吗？
                String arg = "hello" + num.incrementAndGet();
                String res = car.ooxx(arg);
                System.out.println("client over msg: " + res + " src arg: " + arg);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testRPC() {

        Car car = MyProxy.proxyGet(Car.class);
        Persion zhangsan = car.oxox("zhangsan", 16);
        System.out.println(zhangsan);
    }








    @Test
    public void testRpcLocal() {
        new Thread(() -> {
            startServer();
        }).start();

        System.out.println("server started......");

        Car car = MyProxy.proxyGet(Car.class);
        Persion zhangsan = car.oxox("zhangsan", 16);
        System.out.println(zhangsan);
    }



}
