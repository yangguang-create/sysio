package com.bjmashibing.system.rpcdemo.rpc.transport;


import com.bjmashibing.system.rpcdemo.util.SerDerUtil;
import com.bjmashibing.system.rpcdemo.rpc.ResponseMappingCallback;
import com.bjmashibing.system.rpcdemo.rpc.protocol.MyContent;
import com.bjmashibing.system.rpcdemo.rpc.protocol.Myheader;
import com.sean.io.test.rpc.transport.ClientResponses;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: 马士兵教育
 * @create: 2020-08-16 20:51
 */
public class ClientFactory{

    int poolSize = 5;
    NioEventLoopGroup clientWorker;
    Random rand = new Random();
    private ClientFactory(){}
    private static final ClientFactory factory;
    static {
        factory = new ClientFactory();
    }
    public static ClientFactory getFactory(){
        return factory;
    }


    public static  CompletableFuture<Object>    transport(MyContent content){
        byte[] msgBody = SerDerUtil.ser(content);
        Myheader header = Myheader.createHeader(msgBody);
        byte[] msgHeader = SerDerUtil.ser(header);
        System.out.println("main:::"+ msgHeader.length);


        NioSocketChannel clientChannel = factory.getClient(new InetSocketAddress("localhost", 9090));

        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(msgHeader.length + msgBody.length);

        long id = header.getRequestID();
        CompletableFuture<Object> res = new CompletableFuture<>();
        ResponseMappingCallback.addCallBack(id, res);
        byteBuf.writeBytes(msgHeader);
        byteBuf.writeBytes(msgBody);
        ChannelFuture channelFuture = clientChannel.writeAndFlush(byteBuf);

        return res;
    }




    //一个consumer 可以连接很多的provider，每一个provider都有自己的pool  K,V

    ConcurrentHashMap<InetSocketAddress, ClientPool> outboxs = new ConcurrentHashMap<>();




    public  NioSocketChannel getClient(InetSocketAddress address){

        //TODO 在并发情况下一定要谨慎
        ClientPool clientPool = outboxs.get(address);
        if(clientPool ==  null){
            synchronized(outboxs){
                if(clientPool ==  null){
                    outboxs.putIfAbsent(address,new ClientPool(poolSize));
                    clientPool =  outboxs.get(address);
                }
            }
        }

        int i = rand.nextInt(poolSize);

        if( clientPool.clients[i] != null && clientPool.clients[i].isActive()){
            return clientPool.clients[i];
        }else{
            synchronized (clientPool.lock[i]){
                if(clientPool.clients[i] == null || ! clientPool.clients[i].isActive())
                clientPool.clients[i] = create(address);
            }
        }
        return  clientPool.clients[i];

    }

    private NioSocketChannel create(InetSocketAddress address){

        //基于 netty 的客户端创建方式
        clientWorker = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(clientWorker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ServerDecode());
                        p.addLast(new ClientResponses());  //解决给谁的？？  requestID..
                    }
                }).connect(address);
        try {
            NioSocketChannel client = (NioSocketChannel)connect.sync().channel();
            return client;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;


    }


}

