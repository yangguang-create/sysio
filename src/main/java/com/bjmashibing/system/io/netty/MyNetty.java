package com.bjmashibing.system.io.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author: 马士兵教育
 * @create: 2020-06-30 20:02
 */
public class MyNetty {

    /*

    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.11</version>
    </dependency>


    今天主要是netty的初级使用，如果对初级知识过敏的小伙伴可以
    先学点高级的 -。-
    非常初级。。。。
     */

    /*
    目的：前边 NIO 逻辑
    恶心的版本---依托着前面的思维逻辑
    channel  bytebuffer  selector
    bytebuffer ---> 在netty中对应的是 bytebuf【pool】在netty中是池化的概念.
     */


    @Test
    public void myBytebuf(){

        //分配内存的方式：总体来说是：是否池化，堆内的，堆外的.
//        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(8, 20);
        //pool：非池化的分配
//        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
         buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
         buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
         buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
         buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        //超过会报错.writerIndex(20) + minWritableBytes(4) exceeds maxCapacity(20): PooledUnsafeHeapByteBuf(ridx: 0, widx: 20, cap: 20/20)
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);




    }

    public static void print(ByteBuf buf){
        System.out.println("buf.isReadable()    :"+buf.isReadable());
        System.out.println("buf.readerIndex()   :"+buf.readerIndex());
        System.out.println("buf.readableBytes() "+buf.readableBytes());
        System.out.println("buf.isWritable()    :"+buf.isWritable());
        System.out.println("buf.writerIndex()   :"+buf.writerIndex());
        System.out.println("buf.writableBytes() :"+buf.writableBytes());
        System.out.println("buf.capacity()  :"+buf.capacity());
        System.out.println("buf.maxCapacity()   :"+buf.maxCapacity());
        System.out.println("buf.isDirect()  :"+buf.isDirect());
        System.out.println("--------------");






    }


    /*
    客户端
    连接别人
    1，主动发送数据
    2，别人什么时候给我发？  event  selector
     */

    @Test
    public void loopExecutor() throws Exception {
        //group就是线程池的概念
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(()->{
            try {
                for (;;){
                    System.out.println("hello world001");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
 selector.execute(()->{
            try {
                for (;;){
                    System.out.println("hello world002");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });


        System.in.read();
    }



    @Test
    public void clientMode() throws Exception {
        //NiOEventLoopGroup相当于一个线程，这个线程中有一个多路复用器selector.
        NioEventLoopGroup thread = new NioEventLoopGroup(1);

        //客户端模式：
        NioSocketChannel client = new NioSocketChannel();

        //将client注册到多路复用器中.
        thread.register(client);  //epoll_ctl(5,ADD,3):把3注册到5中.

        //响应式：响应服务器发送过来的事件。
        ChannelPipeline p = client.pipeline();//通过pipeline()得到读事件，读事件触发后，会多路复用器会调用针对读事件的Handler
        p.addLast(new MyInHandler());

        //发送的操作
        //reactor  异步的特征:每一步操作大部分都是异步的，比如，连接这不操作就是异步的.连上了，但是数据没有发送过来.
        ChannelFuture connect = client.connect(new InetSocketAddress("192.168.150.11", 9090));
        //保证连接成功
        ChannelFuture sync = connect.sync();

        ByteBuf buf = Unpooled.copiedBuffer("hello server".getBytes());
        //发送数据也是异步的.
        ChannelFuture send = client.writeAndFlush(buf);
        //保证发送成功
        send.sync();

        //马老师的多线程
        //处理异步的问题，避免客户端退出.
        sync.channel().closeFuture().sync();

        System.out.println("client over....");

    }

    @Test
    public void nettyClient() throws InterruptedException {

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        //自己实现的时候，需要关注client的注册，读取等前后顺序，还需要关注相关方法的异步问题.
        //netty准备了bootstrap.来简化一些细节.
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(group)//添加到selector
                .channel(NioSocketChannel.class)//准备socketchannel
//                .handler(new ChannelInit())//未来客户端一连接，需要由什么handler处理
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })//官方的写法，预埋的handler
                .connect(new InetSocketAddress("192.168.150.11", 9090));//连接

        Channel client = connect.sync().channel();//保证连接成功

        //服务端向客户端发送数据.
        ByteBuf buf = Unpooled.copiedBuffer("hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        client.closeFuture().sync();//关注客户端关闭的事件

    }


    /**
     * 服务端的写法：
     * 不论是客户端，还是服务端，在netty中都是基于响应式的多路复用器的事件驱动模型.
     * 所以：服务端也应该是由事件驱动的。
     * @throws Exception
     */
    @Test
    public void serverMode() throws Exception {

        NioEventLoopGroup thread = new NioEventLoopGroup(1);//多路复用器
        NioServerSocketChannel server = new NioServerSocketChannel();


        thread.register(server);//并把server注册到多路复用器中

        //指不定什么时候家里来人。。响应式：需要响应式方式来接收客户端的连接。
        ChannelPipeline p = server.pipeline();
        //在pipeline中预埋未来事件发生该怎么处理的handler。
        p.addLast(new MyAcceptHandler(thread,new ChannelInit()));  //accept接收客户端，并且注册到selector：不仅要接收，还要把接收的客户端注册到selector.
//        p.addLast(new MyAcceptHandler(thread,new MyInHandler()));  //accept接收客户端，并且注册到selector

        //绑定的时候，需要得到绑定的结果，避免直接退出.
        ChannelFuture bind = server.bind(new InetSocketAddress("192.168.150.1", 9090));


        bind.sync().channel().closeFuture().sync();//等待服务端关闭后才关闭.
        System.out.println("server close....");


    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ServerBootstrap bs = new ServerBootstrap();//服务端使用的是ServerBootstrap

        //服务端不需要acceptHandler了，因为，在netty内部帮我们做了.
        ChannelFuture bind = bs.group(group, group)//添加的时候，需要两个group，一个是boss：用来做accept，一个是worker
                .channel(NioServerSocketChannel.class)//服务端的对象NioServerSocketChannel
//                .childHandler(new ChannelInit())
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })
                .bind(new InetSocketAddress("192.168.150.1", 9090));

        //sync():等着绑定成功.
        //closeFuture():等着关闭.
        //sync():服务端阻塞.
        bind.sync().channel().closeFuture().sync();

    }







}

/**
 * 服务端应该处理的Handler。
 */
class  MyAcceptHandler  extends ChannelInboundHandlerAdapter{


    private final EventLoopGroup selector;
    private final ChannelHandler handler;

    public MyAcceptHandler(EventLoopGroup thread, ChannelHandler myInHandler) {
        this.selector = thread;
        this.handler = myInHandler;  //ChannelInit
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server registerd...");
    }

    /**
     * 对于服务端的listen状态的socket来说，读到的是客户端连接过来的socket。
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //  listen  socket   只能accept,读不到消息    client的socket，只能用来读数据.
        //  socket           R/W
        //在listen的状态中，读到的是客户端连进来的Socket
        SocketChannel client = (SocketChannel) msg;  //accept接收的操作框架给我们做了.所以：应该找到框架中哪一步做的接收操作。对于server来说，是accept(),对于客户端来说，是read().
        //2，响应式的  handler
        ChannelPipeline p = client.pipeline();
        p.addLast(handler);  //1,client::pipeline[ChannelInit,]

        //1，注册
        selector.register(client);


    }
}

//为啥要有一个inithandler，可以没有，但是MyInHandler就得设计成单例.
//不处理业务，只处理被注册之后，在管道中添加处理数据的handler即可，而且，每个客户端添加的都是一个新new 出来的handler
@ChannelHandler.Sharable
class ChannelInit extends ChannelInboundHandlerAdapter{

    /**
     * ChannelInit是第一个Handler,在pipeline中是第一个handler，
     *  accept注册完了后，会调用channelRegistered()，这个方法中，
     *  就在管道中在放一个管道.
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel client = ctx.channel();
        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());//2,client::pipeline[ChannelInit,MyInHandler]
        ctx.pipeline().remove(this);
        //3,client::pipeline[MyInHandler]
    }

//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        System.out.println("haha");
//        super.channelRead(ctx, msg);
//    }
}


/*
acceptHandler一般都是由框架来实现，
读写数据的Handler一般都是由用户自己实现.
所以，不能给用户代码增加限制.
    可以让客户端，每次使用这个handler的时候，都是new 的新的，这样，多个连接就不会共用这一个handler了。
就是用户自己实现的，你能说让用户放弃属性的操作吗
@ChannelHandler.Sharable  不应该被强压给coder
 */
class MyInHandler extends ChannelInboundHandlerAdapter {

    /**
     * channel的注册成功的方法
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client  registed...");
    }

    /**
     *判断channel是否 存活
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client active...");
    }

    /**
     * 读取服务端的内容.
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //netty把客户端发送过来的内容转换成ByteBuf.
        ByteBuf buf = (ByteBuf) msg;
        //read write 操作ByteBuf的时候，会移动Buf的指针.
        //get set 操作ByteBuf的时候，不会移动指针.
//        CharSequence str = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);//读成字符序列.
        CharSequence str = buf.getCharSequence(0,buf.readableBytes(), CharsetUtil.UTF_8);
        System.out.println(str);
        //接收完后，可以继续对服务器写。
        ctx.writeAndFlush(buf);
    }
}